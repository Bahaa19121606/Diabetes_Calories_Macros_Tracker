package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

enum class OvernightTrend { DROPPING, STABLE, RISING }

/** Why a night doesn't count as a basal test. */
enum class NightExclusion {
    /** NovoRapid within 3 h before the bedtime reading. */
    BOLUS_BEFORE_BED,
    /** A meal within 3 h before the bedtime reading. */
    FOOD_BEFORE_BED,
    /** NovoRapid or food between bedtime and the fasting reading (including treating a low). */
    BOLUS_OR_FOOD_OVERNIGHT,
    /** Workout that day: never adjust basal to compensate for workouts. */
    WORKOUT_DAY,
    /** Before the current Tresiba dose was started. */
    PREVIOUS_DOSE,
}

data class Night(
    /** The morning of the fasting reading. */
    val date: LocalDate,
    val bedtime: GlucoseReading,
    val fasting: GlucoseReading,
    val lowestMgDl: Int,
    val exclusion: NightExclusion?,
) {
    val changeMgDl: Int get() = fasting.mgDl - bedtime.mgDl
    val trend: OvernightTrend
        get() = when {
            changeMgDl < -TresibaAnalyzer.TREND_THRESHOLD_MG_DL -> OvernightTrend.DROPPING
            changeMgDl > TresibaAnalyzer.TREND_THRESHOLD_MG_DL -> OvernightTrend.RISING
            else -> OvernightTrend.STABLE
        }
    val hadLow: Boolean get() = lowestMgDl < GlucoseLimits.LOW_MG_DL
    val isClean: Boolean get() = exclusion == null
}

sealed interface TresibaRecommendation {
    data object NoDoseLogged : TresibaRecommendation

    /** Safety override: lows on clean nights. Applies even during the 4-day wait. */
    data class ReduceForLows(val currentDose: Double, val newDose: Double, val veryLow: Boolean) : TresibaRecommendation

    /** Dose changed less than 4 days ago; Tresiba hasn't reached steady state. */
    data class Wait(val currentDose: Double, val until: Instant) : TresibaRecommendation

    data class NeedMoreData(val currentDose: Double, val consecutiveCleanNights: Int) : TresibaRecommendation

    data class Reduce(val currentDose: Double, val newDose: Double) : TresibaRecommendation

    /** Rising 3 nights running: +[minIncrease]..[maxIncrease] units; the smaller step is suggested. */
    data class Increase(val currentDose: Double, val minIncrease: Int, val maxIncrease: Int) : TresibaRecommendation

    data class Hold(val currentDose: Double) : TresibaRecommendation
}

data class TresibaReport(
    val currentDose: Double?,
    val lastChangeAt: Instant?,
    /** Newest first. */
    val nights: List<Night>,
    val recommendation: TresibaRecommendation,
)

/**
 * The basal part of the protocol: compare each bedtime reading with the next morning's fasting
 * reading, only on "clean" nights, and require the same trend 3 nights running. Tresiba takes
 * 3–4 days to reach steady state, so there's a 4-day wait after any dose change.
 */
object TresibaAnalyzer {
    const val TREND_THRESHOLD_MG_DL = 30
    const val NIGHTS_REQUIRED = 3
    val STEADY_STATE_WAIT: Duration = Duration.ofDays(4)
    val CLEAN_WINDOW_BEFORE_BED: Duration = Duration.ofHours(3)
    /** A bedtime reading counts for a fasting reading 4–14 h later. */
    private val MIN_NIGHT: Duration = Duration.ofHours(4)
    private val MAX_NIGHT: Duration = Duration.ofHours(14)

    fun analyze(
        readings: List<GlucoseReading>,
        doses: List<InsulinDose>,
        meals: List<Meal>,
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
    ): TresibaReport {
        val tresiba = doses.filter { it.type == InsulinType.TRESIBA && !it.administeredAt.isAfter(now) }
            .sortedBy { it.administeredAt }
        val currentDose = tresiba.lastOrNull()?.units
        val lastChangeAt = tresiba.zipWithNext().lastOrNull { (a, b) -> a.units != b.units }?.second?.administeredAt

        val nights = findNights(readings, doses, meals, workouts, lastChangeAt, zone)
        if (currentDose == null) return TresibaReport(null, lastChangeAt, nights, TresibaRecommendation.NoDoseLogged)

        return TresibaReport(currentDose, lastChangeAt, nights, recommend(currentDose, lastChangeAt, nights, now, zone))
    }

    private fun recommend(
        currentDose: Double,
        lastChangeAt: Instant?,
        nights: List<Night>,
        now: Instant,
        zone: ZoneId,
    ): TresibaRecommendation {
        val today = now.atZone(zone).toLocalDate()
        val recentClean = nights.filter { it.isClean && !it.date.isBefore(today.minusDays(2)) }
        val veryLow = recentClean.any { it.lowestMgDl < GlucoseLimits.VERY_LOW_MG_DL }
        if (veryLow || recentClean.count { it.hadLow } >= 2) {
            return TresibaRecommendation.ReduceForLows(currentDose, currentDose - reductionStep(currentDose), veryLow)
        }

        if (lastChangeAt != null && now.isBefore(lastChangeAt.plus(STEADY_STATE_WAIT))) {
            return TresibaRecommendation.Wait(currentDose, lastChangeAt.plus(STEADY_STATE_WAIT))
        }

        // The latest run of consecutive clean nights, which must be recent (ending today or yesterday).
        val clean = nights.filter { it.isClean }
        var run = 0
        val latest = clean.firstOrNull()?.date
        if (latest != null && !latest.isBefore(today.minusDays(1))) {
            var expected: LocalDate = latest
            for (night in clean) {
                if (night.date != expected) break
                run++
                expected = expected.minusDays(1)
            }
        }
        if (run < NIGHTS_REQUIRED) return TresibaRecommendation.NeedMoreData(currentDose, run)

        val trends = clean.take(NIGHTS_REQUIRED).map { it.trend }.toSet()
        return when (trends.singleOrNull()) {
            OvernightTrend.DROPPING -> TresibaRecommendation.Reduce(currentDose, currentDose - reductionStep(currentDose))
            OvernightTrend.RISING -> TresibaRecommendation.Increase(currentDose, 2, max(2, (currentDose * 0.1).roundToInt()))
            else -> TresibaRecommendation.Hold(currentDose)
        }
    }

    /** About 10%, in whole units: 4 u at 42 u. */
    fun reductionStep(dose: Double): Int = max(1, (dose * 0.1).roundToInt())

    /** Newest first; at most one night per morning. */
    fun findNights(
        readings: List<GlucoseReading>,
        doses: List<InsulinDose>,
        meals: List<Meal>,
        workouts: List<Workout>,
        lastChangeAt: Instant?,
        zone: ZoneId,
    ): List<Night> {
        val bedtimes = readings.filter { it.context == GlucoseContext.BEDTIME }
        val novorapid = doses.filter { it.type == InsulinType.NOVORAPID }

        return readings.filter { it.context == GlucoseContext.FASTING }
            .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
            .mapNotNull { (date, fastings) ->
                val fasting = fastings.minBy { it.measuredAt } // first reading of the morning
                val bedtime = bedtimes
                    .filter {
                        val gap = Duration.between(it.measuredAt, fasting.measuredAt)
                        gap >= MIN_NIGHT && gap <= MAX_NIGHT
                    }
                    .maxByOrNull { it.measuredAt }
                    ?: return@mapNotNull null
                val b = bedtime.measuredAt
                val f = fasting.measuredAt
                val beforeBed = b.minus(CLEAN_WINDOW_BEFORE_BED)
                fun Instant.inBeforeBed() = !isBefore(beforeBed) && !isAfter(b)
                fun Instant.overnight() = isAfter(b) && isBefore(f)
                val bedDayStart = b.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

                val exclusion = when {
                    lastChangeAt != null && b.isBefore(lastChangeAt) -> NightExclusion.PREVIOUS_DOSE
                    novorapid.any { it.administeredAt.inBeforeBed() } -> NightExclusion.BOLUS_BEFORE_BED
                    meals.any { it.eatenAt.inBeforeBed() } -> NightExclusion.FOOD_BEFORE_BED
                    novorapid.any { it.administeredAt.overnight() } || meals.any { it.eatenAt.overnight() } ->
                        NightExclusion.BOLUS_OR_FOOD_OVERNIGHT
                    workouts.any { !it.startedAt.isBefore(bedDayStart) && it.startedAt.isBefore(f) } -> NightExclusion.WORKOUT_DAY
                    else -> null
                }
                val lowest = readings
                    .filter { !it.measuredAt.isBefore(b) && !it.measuredAt.isAfter(f) }
                    .minOf { it.mgDl }
                Night(date, bedtime, fasting, lowest, exclusion)
            }
            .sortedByDescending { it.date }
    }
}
