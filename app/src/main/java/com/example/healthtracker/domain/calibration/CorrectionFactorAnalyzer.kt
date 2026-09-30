package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Why a correction doesn't count toward the correction-factor suggestion. */
enum class CorrectionExclusion {
    DIFFERENT_FACTOR,
    NO_READING_BEFORE,
    /** No reading 2.5–4.5 h later, when NovoRapid has done most of its work. */
    NO_FOLLOW_UP_READING,
    /** Other NovoRapid still active, or taken before the follow-up reading. */
    OTHER_INSULIN,
    FOOD,
    WORKOUT,
}

data class CorrectionResponse(
    val dose: InsulinDose,
    val before: GlucoseReading?,
    val after: GlucoseReading?,
    val exclusion: CorrectionExclusion?,
) {
    val dropMgDl: Int? get() = if (before != null && after != null) before.mgDl - after.mgDl else null

    /** What this correction showed: mg/dL dropped per unit. */
    val observedFactor: Double? get() = dropMgDl?.let { it / dose.units }
    val qualifies: Boolean get() = exclusion == null
}

sealed interface CorrectionFactorRecommendation {
    /** Safety override: a qualifying correction went low. Applies before the 72 h are up. */
    data class WeakerForLows(val currentFactor: Double, val newFactor: Double) : CorrectionFactorRecommendation

    data class NeedMoreTime(val currentFactor: Double, val readyAt: Instant?) : CorrectionFactorRecommendation
    data class NeedMoreCorrections(val currentFactor: Double, val qualifyingCorrections: Int) : CorrectionFactorRecommendation

    /** Corrections dropped less than expected: a smaller number, i.e. more insulin per mg/dL. */
    data class Stronger(val currentFactor: Double, val newFactor: Double, val observedFactor: Double) : CorrectionFactorRecommendation

    /** Corrections dropped more than expected: a bigger number, i.e. less insulin per mg/dL. */
    data class Weaker(val currentFactor: Double, val newFactor: Double, val observedFactor: Double) : CorrectionFactorRecommendation

    data class Hold(val currentFactor: Double, val observedFactor: Double) : CorrectionFactorRecommendation
}

data class CorrectionFactorReport(
    val currentFactor: Double,
    /** Newest first; calculator correction-only doses. */
    val responses: List<CorrectionResponse>,
    val recommendation: CorrectionFactorRecommendation,
)

/**
 * Watches correction-only doses (no food) given with the calculator at the current correction
 * factor, comparing the reading before with one ~3 h later. After 72 h of monitoring and at least
 * 3 qualifying corrections, suggests moving toward the observed drop per unit when 2 out of 3
 * corrections are more than 20% off. Each change is capped at 20%.
 */
object CorrectionFactorAnalyzer {
    val MONITORING_PERIOD: Duration = Duration.ofHours(72)
    const val CORRECTIONS_REQUIRED = 3
    /** A correction is "off" when its observed factor differs from the current one by more than this. */
    const val TOLERANCE = 0.2
    const val MAX_STEP = 0.2
    const val ROUND_TO_MG_DL = 5

    private val BEFORE_WINDOW = Duration.ofMinutes(30)
    private val BEFORE_AFTER_DOSE = Duration.ofMinutes(5)
    val FOLLOW_UP_EARLIEST: Duration = Duration.ofMinutes(150)
    private val AFTER_LATEST = Duration.ofMinutes(270)
    val FOLLOW_UP_IDEAL: Duration = Duration.ofHours(3)
    private val FOOD_BEFORE = Duration.ofHours(2)
    private val WORKOUT_BEFORE = Duration.ofHours(6)

    fun analyze(
        currentFactor: Double,
        doses: List<InsulinDose>,
        meals: List<Meal>,
        readings: List<GlucoseReading>,
        workouts: List<Workout>,
        now: Instant,
    ): CorrectionFactorReport {
        val novorapid = doses.filter { it.type == InsulinType.NOVORAPID }
        val corrections = novorapid
            .filter { dose -> dose.bolus.let { it != null && it.carbsGrams == 0 && it.correctionUnits > 0 } && !dose.administeredAt.isAfter(now) }
            .sortedByDescending { it.administeredAt }

        val responses = corrections.map { evaluate(it, currentFactor, novorapid, meals, readings, workouts) }
        val monitoringStart = corrections.filter { it.bolus!!.correctionFactor == currentFactor }.minOfOrNull { it.administeredAt }
        return CorrectionFactorReport(currentFactor, responses, recommend(currentFactor, responses, monitoringStart, now))
    }

    private fun recommend(
        factor: Double,
        responses: List<CorrectionResponse>,
        monitoringStart: Instant?,
        now: Instant,
    ): CorrectionFactorRecommendation {
        val qualifying = responses.filter { it.qualifies }
        if (qualifying.any { it.after!!.mgDl < GlucoseLimits.LOW_MG_DL }) {
            return CorrectionFactorRecommendation.WeakerForLows(factor, step(factor, factor * (1 + MAX_STEP)))
        }
        val readyAt = monitoringStart?.plus(MONITORING_PERIOD)
        if (readyAt == null || now.isBefore(readyAt)) return CorrectionFactorRecommendation.NeedMoreTime(factor, readyAt)
        if (qualifying.size < CORRECTIONS_REQUIRED) return CorrectionFactorRecommendation.NeedMoreCorrections(factor, qualifying.size)

        val observed = qualifying.map { it.observedFactor!! }
        val median = observed.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        val majority = ceil(qualifying.size * 2.0 / 3.0).toInt()
        return when {
            observed.count { it > factor * (1 + TOLERANCE) } >= majority && median > factor ->
                CorrectionFactorRecommendation.Weaker(factor, step(factor, median), median)
            observed.count { it < factor * (1 - TOLERANCE) } >= majority && median < factor ->
                CorrectionFactorRecommendation.Stronger(factor, step(factor, median), median)
            else -> CorrectionFactorRecommendation.Hold(factor, median)
        }
    }

    /** Moves toward [target] by at most 20%, rounded to 5 mg/dL, always changing by at least 5. */
    fun step(current: Double, target: Double): Double {
        val capped = target.coerceIn(current * (1 - MAX_STEP), current * (1 + MAX_STEP))
        var rounded = (capped / ROUND_TO_MG_DL).roundToInt() * ROUND_TO_MG_DL.toDouble()
        if (rounded == current) rounded += if (target > current) ROUND_TO_MG_DL else -ROUND_TO_MG_DL
        return rounded.coerceAtLeast(ROUND_TO_MG_DL.toDouble())
    }

    private fun evaluate(
        dose: InsulinDose,
        currentFactor: Double,
        novorapid: List<InsulinDose>,
        meals: List<Meal>,
        readings: List<GlucoseReading>,
        workouts: List<Workout>,
    ): CorrectionResponse {
        val t = dose.administeredAt
        val before = readings
            .filter { !it.measuredAt.isBefore(t.minus(BEFORE_WINDOW)) && !it.measuredAt.isAfter(t.plus(BEFORE_AFTER_DOSE)) }
            .maxByOrNull { it.measuredAt }
        val after = readings
            .filter { !it.measuredAt.isBefore(t.plus(FOLLOW_UP_EARLIEST)) && !it.measuredAt.isAfter(t.plus(AFTER_LATEST)) }
            .minByOrNull { abs(Duration.between(t.plus(FOLLOW_UP_IDEAL), it.measuredAt).toMillis()) }

        val exclusion = when {
            dose.bolus!!.correctionFactor != currentFactor -> CorrectionExclusion.DIFFERENT_FACTOR
            before == null -> CorrectionExclusion.NO_READING_BEFORE
            after == null -> CorrectionExclusion.NO_FOLLOW_UP_READING
            novorapid.any {
                it.id != dose.id && it.administeredAt.isAfter(t.minus(InsulinOnBoard.DURATION)) && !it.administeredAt.isAfter(after.measuredAt)
            } -> CorrectionExclusion.OTHER_INSULIN
            meals.any { it.eatenAt.isAfter(t.minus(FOOD_BEFORE)) && it.eatenAt.isBefore(after.measuredAt) } -> CorrectionExclusion.FOOD
            workouts.any { !it.endedAt.isBefore(t.minus(WORKOUT_BEFORE)) && it.startedAt.isBefore(after.measuredAt) } ->
                CorrectionExclusion.WORKOUT
            else -> null
        }
        return CorrectionResponse(dose, before, after, exclusion)
    }
}
