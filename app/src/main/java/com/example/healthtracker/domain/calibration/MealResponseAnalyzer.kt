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

/** Why a meal doesn't count toward the carb-ratio suggestion. */
enum class MealExclusion {
    DIFFERENT_RATIO,
    NO_PRE_MEAL_READING,
    /** Pre-meal glucose outside 80–180: the correction part would dominate. */
    PRE_MEAL_OUT_OF_RANGE,
    NO_POST_MEAL_READING,
    /** > 30 g fat or > 40 g protein: absorption peaks too late for a 2-hour reading. */
    HIGH_FAT_OR_PROTEIN,
    /** More NovoRapid or food before the 2-hour reading. */
    EXTRA_INSULIN_OR_FOOD,
    WORKOUT,
}

data class MealResponse(
    val dose: InsulinDose,
    val meal: Meal?,
    val preMeal: GlucoseReading?,
    val postMeal: GlucoseReading?,
    val exclusion: MealExclusion?,
) {
    val riseMgDl: Int? get() = if (preMeal != null && postMeal != null) postMeal.mgDl - preMeal.mgDl else null
    val qualifies: Boolean get() = exclusion == null
}

sealed interface CarbRatioRecommendation {
    /** Safety override: a qualifying meal went low at 2 h. Applies before the 72 h are up. */
    data class WeakerForLows(val currentRatio: Double, val newRatio: Double) : CarbRatioRecommendation

    data class NeedMoreTime(val currentRatio: Double, val readyAt: Instant?) : CarbRatioRecommendation
    data class NeedMoreMeals(val currentRatio: Double, val qualifyingMeals: Int) : CarbRatioRecommendation

    /** Rising a lot at 2 h: more insulin per gram. */
    data class Stronger(val currentRatio: Double, val newRatio: Double) : CarbRatioRecommendation

    /** Dropping at 2 h: less insulin per gram. */
    data class Weaker(val currentRatio: Double, val newRatio: Double) : CarbRatioRecommendation

    data class Hold(val currentRatio: Double) : CarbRatioRecommendation
}

data class MealResponseReport(
    val currentRatio: Double,
    /** Newest first; calculator doses only. */
    val responses: List<MealResponse>,
    val recommendation: CarbRatioRecommendation,
)

/**
 * Watches real meals dosed with the calculator at the current ratio. After 72 h of monitoring
 * and at least 3 qualifying meals, suggests a ~10% ratio change when 2 out of 3 meals agree.
 */
object MealResponseAnalyzer {
    val MONITORING_PERIOD: Duration = Duration.ofHours(72)
    const val MEALS_REQUIRED = 3
    const val RISE_TOO_HIGH_MG_DL = 60
    const val DROP_TOO_LOW_MG_DL = 30
    val PRE_MEAL_RANGE = 80..180
    const val MAX_FAT_GRAMS = 30
    const val MAX_PROTEIN_GRAMS = 40

    private val MEAL_MATCH = Duration.ofMinutes(30)
    private val PRE_BEFORE = Duration.ofMinutes(30)
    private val PRE_AFTER = Duration.ofMinutes(5)
    private val POST_EARLIEST = Duration.ofMinutes(105)
    private val POST_LATEST = Duration.ofMinutes(150)
    private val POST_IDEAL = Duration.ofHours(2)
    private val WORKOUT_BEFORE = Duration.ofHours(6)

    fun analyze(
        currentRatio: Double,
        doses: List<InsulinDose>,
        meals: List<Meal>,
        readings: List<GlucoseReading>,
        workouts: List<Workout>,
        now: Instant,
    ): MealResponseReport {
        val novorapid = doses.filter { it.type == InsulinType.NOVORAPID }
        val calculatorDoses = novorapid
            .filter { dose -> dose.bolus.let { it != null && it.carbsGrams > 0 } && !dose.administeredAt.isAfter(now) }
            .sortedByDescending { it.administeredAt }

        val responses = calculatorDoses.map { dose -> evaluate(dose, currentRatio, novorapid, meals, readings, workouts) }

        val monitoringStart = calculatorDoses.filter { it.bolus!!.carbRatio == currentRatio }.minOfOrNull { it.administeredAt }
        return MealResponseReport(currentRatio, responses, recommend(currentRatio, responses, monitoringStart, now))
    }

    private fun recommend(
        ratio: Double,
        responses: List<MealResponse>,
        monitoringStart: Instant?,
        now: Instant,
    ): CarbRatioRecommendation {
        val qualifying = responses.filter { it.qualifies }
        if (qualifying.any { it.postMeal!!.mgDl < GlucoseLimits.LOW_MG_DL }) {
            return CarbRatioRecommendation.WeakerForLows(ratio, DoseMath.weakerRatio(ratio))
        }
        val readyAt = monitoringStart?.plus(MONITORING_PERIOD)
        if (readyAt == null || now.isBefore(readyAt)) return CarbRatioRecommendation.NeedMoreTime(ratio, readyAt)
        if (qualifying.size < MEALS_REQUIRED) return CarbRatioRecommendation.NeedMoreMeals(ratio, qualifying.size)

        val majority = ceil(qualifying.size * 2.0 / 3.0).toInt()
        val rises = qualifying.map { it.riseMgDl!! }
        return when {
            rises.count { it > RISE_TOO_HIGH_MG_DL } >= majority ->
                CarbRatioRecommendation.Stronger(ratio, DoseMath.strongerRatio(ratio))
            rises.count { it < -DROP_TOO_LOW_MG_DL } >= majority ->
                CarbRatioRecommendation.Weaker(ratio, DoseMath.weakerRatio(ratio))
            else -> CarbRatioRecommendation.Hold(ratio)
        }
    }

    private fun evaluate(
        dose: InsulinDose,
        currentRatio: Double,
        novorapid: List<InsulinDose>,
        meals: List<Meal>,
        readings: List<GlucoseReading>,
        workouts: List<Workout>,
    ): MealResponse {
        val t = dose.administeredAt
        val meal = meals.firstOrNull { it.id == dose.mealId }
            ?: meals.filter { abs(Duration.between(t, it.eatenAt).toMinutes()) <= MEAL_MATCH.toMinutes() }
                .minByOrNull { abs(Duration.between(t, it.eatenAt).toMillis()) }
        val pre = readings
            .filter { !it.measuredAt.isBefore(t.minus(PRE_BEFORE)) && !it.measuredAt.isAfter(t.plus(PRE_AFTER)) }
            .maxByOrNull { it.measuredAt }
        val post = readings
            .filter { !it.measuredAt.isBefore(t.plus(POST_EARLIEST)) && !it.measuredAt.isAfter(t.plus(POST_LATEST)) }
            .minByOrNull { abs(Duration.between(t.plus(POST_IDEAL), it.measuredAt).toMillis()) }

        val exclusion = when {
            dose.bolus!!.carbRatio != currentRatio -> MealExclusion.DIFFERENT_RATIO
            pre == null -> MealExclusion.NO_PRE_MEAL_READING
            pre.mgDl !in PRE_MEAL_RANGE -> MealExclusion.PRE_MEAL_OUT_OF_RANGE
            post == null -> MealExclusion.NO_POST_MEAL_READING
            meal != null && (meal.macros.fatGrams > MAX_FAT_GRAMS || meal.macros.proteinGrams > MAX_PROTEIN_GRAMS) ->
                MealExclusion.HIGH_FAT_OR_PROTEIN
            novorapid.any { it.id != dose.id && it.administeredAt.isAfter(t) && !it.administeredAt.isAfter(post.measuredAt) } ||
                meals.any { it.id != meal?.id && it.eatenAt.isAfter(t.plus(MEAL_MATCH)) && it.eatenAt.isBefore(post.measuredAt) } ->
                MealExclusion.EXTRA_INSULIN_OR_FOOD
            workouts.any { !it.endedAt.isBefore(t.minus(WORKOUT_BEFORE)) && it.startedAt.isBefore(post.measuredAt) } ->
                MealExclusion.WORKOUT
            else -> null
        }
        return MealResponse(dose, meal, pre, post, exclusion)
    }
}
