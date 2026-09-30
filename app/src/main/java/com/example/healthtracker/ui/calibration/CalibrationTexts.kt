package com.example.healthtracker.ui.calibration

import com.example.healthtracker.domain.calibration.CarbRatioRecommendation
import com.example.healthtracker.domain.calibration.CorrectionExclusion
import com.example.healthtracker.domain.calibration.CorrectionFactorAnalyzer
import com.example.healthtracker.domain.calibration.CorrectionFactorRecommendation
import com.example.healthtracker.domain.calibration.MealExclusion
import com.example.healthtracker.domain.calibration.MealResponseAnalyzer
import com.example.healthtracker.domain.calibration.NightExclusion
import com.example.healthtracker.domain.calibration.OvernightTrend
import com.example.healthtracker.domain.calibration.PreconditionKind
import com.example.healthtracker.domain.calibration.TestOutcome
import com.example.healthtracker.domain.calibration.TresibaAnalyzer
import com.example.healthtracker.domain.calibration.TresibaRecommendation
import com.example.healthtracker.domain.calibration.WeeklyContext
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.ui.common.formatCarbRatio
import com.example.healthtracker.ui.common.formatCorrectionFactor
import com.example.healthtracker.ui.common.formatDateTime
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.common.formatGlucose
import com.example.healthtracker.ui.common.formatGlucoseValue
import com.example.healthtracker.ui.common.formatInsulinUnits

/** Headline + explanation for a recommendation card. */
data class Advice(val title: String, val detail: String, val isChange: Boolean, val isUrgent: Boolean = false)

fun tresibaAdvice(rec: TresibaRecommendation, unit: GlucoseUnit): Advice {
    val threshold = formatGlucose(TresibaAnalyzer.TREND_THRESHOLD_MG_DL, unit)
    return when (rec) {
        TresibaRecommendation.NoDoseLogged -> Advice(
            "Log your Tresiba dose",
            "The analysis needs your current Tresiba dose. Log it on the Diabetes tab.",
            isChange = false,
        )
        is TresibaRecommendation.ReduceForLows -> Advice(
            "Reduce Tresiba to ${formatInsulinUnits(rec.newDose)} u",
            (if (rec.veryLow) "You went below ${formatGlucose(54, unit)} overnight. " else "You had lows on 2 of the last 3 clean nights. ") +
                "Lows override the 3-day rule: reduce by about 10% (from ${formatInsulinUnits(rec.currentDose)} u) and tell your diabetes team.",
            isChange = true,
            isUrgent = true,
        )
        is TresibaRecommendation.Wait -> Advice(
            "Hold ${formatInsulinUnits(rec.currentDose)} u until ${formatDateTime(rec.until)}",
            "Tresiba takes 3–4 days to reach a steady state after a dose change, so nights before then don't show the new dose's full effect.",
            isChange = false,
        )
        is TresibaRecommendation.NeedMoreData -> Advice(
            "Keep collecting (${rec.consecutiveCleanNights} of ${TresibaAnalyzer.NIGHTS_REQUIRED} nights)",
            "A change needs the same overnight trend on ${TresibaAnalyzer.NIGHTS_REQUIRED} consecutive clean nights: a bedtime " +
                "and a fasting reading, with no NovoRapid or food in the 3 hours before bed and no workout that day.",
            isChange = false,
        )
        is TresibaRecommendation.Reduce -> Advice(
            "Reduce Tresiba to ${formatInsulinUnits(rec.newDose)} u",
            "Glucose dropped by more than $threshold overnight on 3 nights in a row. Reduce by about 10% " +
                "(from ${formatInsulinUnits(rec.currentDose)} u), then hold the new dose for at least 4 days.",
            isChange = true,
        )
        is TresibaRecommendation.Increase -> Advice(
            "Increase Tresiba to ${formatInsulinUnits(rec.currentDose + rec.minIncrease)} u",
            "Glucose rose by more than $threshold overnight on 3 nights in a row. Increase by ${rec.minIncrease} u " +
                "(up to ${rec.maxIncrease} u if the rise is large), then hold the new dose for at least 4 days.",
            isChange = true,
        )
        is TresibaRecommendation.Hold -> Advice(
            "Keep ${formatInsulinUnits(rec.currentDose)} u",
            "Your last 3 clean nights don't show a consistent overnight trend.",
            isChange = false,
        )
    }
}

fun carbRatioAdvice(rec: CarbRatioRecommendation, unit: GlucoseUnit): Advice = when (rec) {
    is CarbRatioRecommendation.WeakerForLows -> Advice(
        "Change to ${formatCarbRatio(rec.newRatio)}",
        "A meal dosed at ${formatCarbRatio(rec.currentRatio)} went below ${formatGlucose(70, unit)} two hours later. Less insulin per gram " +
            "is suggested straight away, without waiting for the 72 hours.",
        isChange = true,
        isUrgent = true,
    )
    is CarbRatioRecommendation.NeedMoreTime -> Advice(
        "Monitoring ${formatCarbRatio(rec.currentRatio)}",
        rec.readyAt?.let { "A suggestion is possible after ${formatDateTime(it)} (72 hours of monitoring)." }
            ?: "Dose meals with the calculator and log pre-meal and 2-hour readings. A suggestion is possible after 72 hours.",
        isChange = false,
    )
    is CarbRatioRecommendation.NeedMoreMeals -> Advice(
        "Keep collecting (${rec.qualifyingMeals} of ${MealResponseAnalyzer.MEALS_REQUIRED} meals)",
        "A meal counts when its dose came from the calculator, the pre-meal reading was ${range(unit)}, and there's a reading 2 hours later.",
        isChange = false,
    )
    is CarbRatioRecommendation.Stronger -> Advice(
        "Change to ${formatCarbRatio(rec.newRatio)}",
        "Most meals rose more than ${formatGlucose(MealResponseAnalyzer.RISE_TOO_HIGH_MG_DL, unit)} at 2 hours on ${formatCarbRatio(rec.currentRatio)}: slightly more insulin per gram.",
        isChange = true,
    )
    is CarbRatioRecommendation.Weaker -> Advice(
        "Change to ${formatCarbRatio(rec.newRatio)}",
        "Most meals ended more than ${formatGlucose(MealResponseAnalyzer.DROP_TOO_LOW_MG_DL, unit)} below where they started at 2 hours on ${formatCarbRatio(rec.currentRatio)}: slightly less insulin per gram.",
        isChange = true,
    )
    is CarbRatioRecommendation.Hold -> Advice(
        "Keep ${formatCarbRatio(rec.currentRatio)}",
        "Meals on this ratio are landing within the expected range at 2 hours.",
        isChange = false,
    )
}

/** The ratio a recommendation proposes, if any. */
val CarbRatioRecommendation.proposedRatio: Double?
    get() = when (this) {
        is CarbRatioRecommendation.WeakerForLows -> newRatio
        is CarbRatioRecommendation.Stronger -> newRatio
        is CarbRatioRecommendation.Weaker -> newRatio
        else -> null
    }

val OvernightTrend.label: String
    get() = when (this) {
        OvernightTrend.DROPPING -> "Dropping"
        OvernightTrend.STABLE -> "Stable"
        OvernightTrend.RISING -> "Rising"
    }

val NightExclusion.label: String
    get() = when (this) {
        NightExclusion.BOLUS_BEFORE_BED -> "Not counted: NovoRapid within 3 h of bedtime"
        NightExclusion.FOOD_BEFORE_BED -> "Not counted: food within 3 h of bedtime"
        NightExclusion.BOLUS_OR_FOOD_OVERNIGHT -> "Not counted: NovoRapid or food overnight"
        NightExclusion.WORKOUT_DAY -> "Not counted: workout that day"
        NightExclusion.PREVIOUS_DOSE -> "Not counted: before your current dose"
    }

private fun range(unit: GlucoseUnit) =
    "${formatGlucoseValue(80, unit)}–${formatGlucose(180, unit)}"

fun MealExclusion.label(unit: GlucoseUnit): String = when (this) {
    MealExclusion.DIFFERENT_RATIO -> "Dosed at a different ratio"
    MealExclusion.NO_PRE_MEAL_READING -> "No pre-meal reading"
    MealExclusion.PRE_MEAL_OUT_OF_RANGE -> "Pre-meal reading outside ${range(unit)}"
    MealExclusion.NO_POST_MEAL_READING -> "No 2-hour reading"
    MealExclusion.HIGH_FAT_OR_PROTEIN -> "High fat or protein (absorbs too slowly)"
    MealExclusion.EXTRA_INSULIN_OR_FOOD -> "More insulin or food before the 2-hour check"
    MealExclusion.WORKOUT -> "Workout around the meal"
}

fun PreconditionKind.label(unit: GlucoseUnit): String = when (this) {
    PreconditionKind.NO_ACTIVE_INSULIN -> "No NovoRapid in the last 4 hours"
    PreconditionKind.GLUCOSE_IN_RANGE -> "A reading from the last 15 minutes, between ${range(unit)}"
    PreconditionKind.GLUCOSE_STABLE -> "Stable: an earlier reading 20–90 minutes before it, within ${formatGlucose(30, unit)}"
    PreconditionKind.NO_WORKOUT_TODAY -> "A rest day: no workout logged today"
}

fun TestOutcome.describe(ratioTested: Double, suggested: Double?, unit: GlucoseUnit): String = when (this) {
    TestOutcome.GOOD -> "Back within ${formatGlucose(30, unit)} of your starting reading by hour 4: ${formatCarbRatio(ratioTested)} fits."
    TestOutcome.TOO_WEAK -> "Still more than ${formatGlucose(30, unit)} above your start at hour 4: ${formatCarbRatio(ratioTested)} is too weak." +
        (suggested?.let { " Try ${formatCarbRatio(it)}." } ?: "")
    TestOutcome.TOO_STRONG -> "More than ${formatGlucose(30, unit)} below your start at hour 4: ${formatCarbRatio(ratioTested)} is too strong." +
        (suggested?.let { " Try ${formatCarbRatio(it)}." } ?: "")
    TestOutcome.LOW_DURING_TEST -> "You went below ${formatGlucose(70, unit)} during the test, so ${formatCarbRatio(ratioTested)} is too strong." +
        (suggested?.let { " Try ${formatCarbRatio(it)}." } ?: "")
    TestOutcome.INCOMPLETE -> "Ended before the 4-hour reading."
}

private fun perUnit(mgDlPerUnit: Double, unit: GlucoseUnit) = "1 u per ${formatCorrectionFactor(mgDlPerUnit, unit)}"

fun correctionFactorAdvice(rec: CorrectionFactorRecommendation, unit: GlucoseUnit): Advice = when (rec) {
    is CorrectionFactorRecommendation.WeakerForLows -> Advice(
        "Change to ${perUnit(rec.newFactor, unit)}",
        "A correction at ${perUnit(rec.currentFactor, unit)} went below ${formatGlucose(70, unit)}. Less insulin per correction " +
            "is suggested straight away, without waiting for the 72 hours.",
        isChange = true,
        isUrgent = true,
    )
    is CorrectionFactorRecommendation.NeedMoreTime -> Advice(
        "Monitoring ${perUnit(rec.currentFactor, unit)}",
        rec.readyAt?.let { "A suggestion is possible after ${formatDateTime(it)} (72 hours of monitoring)." }
            ?: "Use the dose calculator for correction-only doses (no food), then log a reading about 3 hours later. " +
                "A suggestion is possible after 72 hours.",
        isChange = false,
    )
    is CorrectionFactorRecommendation.NeedMoreCorrections -> Advice(
        "Keep collecting (${rec.qualifyingCorrections} of ${CorrectionFactorAnalyzer.CORRECTIONS_REQUIRED} corrections)",
        "A correction counts when it came from the calculator with no food, there's a reading 2.5–4.5 hours later, " +
            "and no other NovoRapid, food or workout got in the way.",
        isChange = false,
    )
    is CorrectionFactorRecommendation.Stronger -> Advice(
        "Change to ${perUnit(rec.newFactor, unit)}",
        "Your corrections lowered glucose by about ${formatCorrectionFactor(rec.observedFactor, unit)} per unit, less than the " +
            "${formatCorrectionFactor(rec.currentFactor, unit)} expected: slightly more insulin per correction. Changes are capped at 20% at a time.",
        isChange = true,
    )
    is CorrectionFactorRecommendation.Weaker -> Advice(
        "Change to ${perUnit(rec.newFactor, unit)}",
        "Your corrections lowered glucose by about ${formatCorrectionFactor(rec.observedFactor, unit)} per unit, more than the " +
            "${formatCorrectionFactor(rec.currentFactor, unit)} expected: slightly less insulin per correction. Changes are capped at 20% at a time.",
        isChange = true,
    )
    is CorrectionFactorRecommendation.Hold -> Advice(
        "Keep ${perUnit(rec.currentFactor, unit)}",
        "Your corrections lowered glucose by about ${formatCorrectionFactor(rec.observedFactor, unit)} per unit, close enough to the current factor.",
        isChange = false,
    )
}

/** The factor a recommendation proposes, if any. */
val CorrectionFactorRecommendation.proposedFactor: Double?
    get() = when (this) {
        is CorrectionFactorRecommendation.WeakerForLows -> newFactor
        is CorrectionFactorRecommendation.Stronger -> newFactor
        is CorrectionFactorRecommendation.Weaker -> newFactor
        else -> null
    }

val CorrectionExclusion.label: String
    get() = when (this) {
        CorrectionExclusion.DIFFERENT_FACTOR -> "Dosed at a different correction factor"
        CorrectionExclusion.NO_READING_BEFORE -> "No reading before the dose"
        CorrectionExclusion.NO_FOLLOW_UP_READING -> "No reading 2.5–4.5 hours later"
        CorrectionExclusion.OTHER_INSULIN -> "Other NovoRapid active or taken"
        CorrectionExclusion.FOOD -> "Food eaten around it"
        CorrectionExclusion.WORKOUT -> "Workout around it"
    }

fun sleepFirstAdvice(weekly: WeeklyContext, unit: GlucoseUnit): Advice = Advice(
    "Prioritise sleep before increasing Tresiba",
    "Your fasting glucose averaged ${weekly.averageFastingMgDl?.let { formatGlucose(it, unit) } ?: "high"}, but you slept only " +
        "${weekly.averageSleep?.let { formatDuration(it) } ?: "a little"} a night on average. Short sleep raises morning glucose " +
        "on its own, so a few nights of proper rest may bring it down without more insulin.",
    isChange = false,
)
