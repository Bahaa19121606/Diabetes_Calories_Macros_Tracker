package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.model.WorkoutIntensity
import java.time.Duration
import java.time.Instant

enum class BolusNote {
    /** No glucose entered: meal dose only. */
    NO_GLUCOSE,
    /** Latest reading is too old to correct from. */
    GLUCOSE_STALE,
    /** NovoRapid taken < 3 h ago: correcting now would stack insulin. */
    CORRECTION_BLOCKED_RECENT_BOLUS,
    /** Below target: the meal dose was reduced. */
    BELOW_TARGET_REDUCED,
    /** Insulin still active from an earlier dose was taken off the correction. */
    INSULIN_ON_BOARD_SUBTRACTED,
    /** Heavy lifting in the last 6 h: correction halved. */
    HALVED_AFTER_WORKOUT,
    /** Unusually large dose: double-check the inputs. */
    LARGE_DOSE,
}

data class BolusInput(
    /** Null or 0 for a correction-only dose. */
    val carbsGrams: Int?,
    val glucoseMgDl: Int?,
    val glucoseMeasuredAt: Instant?,
    val profile: TherapyProfile,
    /** Recent insulin (at least the last 4 h); non-NovoRapid entries are ignored. */
    val recentDoses: List<InsulinDose>,
    val recentWorkouts: List<Workout>,
    val now: Instant,
)

sealed interface BolusResult {
    /** Below 70 mg/dL: treat the low, no insulin. */
    data class TreatLow(val glucoseMgDl: Int) : BolusResult

    data class Suggestion(
        val mealUnits: Double,
        /** After all safety rules; negative when a below-target reading reduced the meal dose. */
        val correctionUnits: Double,
        val insulinOnBoard: Double,
        /** mealUnits + correctionUnits, never negative, rounded to the nearest whole unit. */
        val totalUnits: Double,
        val notes: Set<BolusNote>,
        val lastNovorapidAt: Instant?,
    ) : BolusResult
}

/**
 * Meal dose = carbs ÷ carb ratio. Correction = (glucose − target) ÷ correction factor, with the
 * protocol's safety rules applied to the *positive* part only (a below-target reduction only ever
 * removes insulin, so it is always applied).
 */
object BolusCalculator {
    val CORRECTION_LOCKOUT: Duration = Duration.ofHours(3)
    val GLUCOSE_MAX_AGE: Duration = Duration.ofMinutes(15)
    val WORKOUT_WINDOW: Duration = Duration.ofHours(6)
    const val LARGE_DOSE_UNITS = 15.0

    fun calculate(input: BolusInput): BolusResult {
        val now = input.now
        val glucose = input.glucoseMgDl
        val glucoseAge = input.glucoseMeasuredAt?.let { Duration.between(it, now) }
        val glucoseFresh = glucose != null && glucoseAge != null && !glucoseAge.isNegative && glucoseAge <= GLUCOSE_MAX_AGE

        if (glucoseFresh && glucose!! < GlucoseLimits.LOW_MG_DL) return BolusResult.TreatLow(glucose)

        val notes = mutableSetOf<BolusNote>()
        val carbs = input.carbsGrams?.takeIf { it > 0 } ?: 0
        val mealUnits = carbs / input.profile.carbRatio
        val insulinOnBoard = InsulinOnBoard.remainingUnits(input.recentDoses, now)
        val lastNovorapidAt = InsulinOnBoard.lastNovorapidAt(input.recentDoses, now)
        val correctionFactor = input.profile.correctionFactor

        var correction = 0.0
        when {
            glucose == null -> notes += BolusNote.NO_GLUCOSE
            !glucoseFresh -> notes += BolusNote.GLUCOSE_STALE
            else -> {
                val raw = (glucose - input.profile.targetMgDl) / correctionFactor
                if (raw < 0) {
                    correction = raw
                    notes += BolusNote.BELOW_TARGET_REDUCED
                } else if (raw > 0) {
                    val sinceLastBolus = lastNovorapidAt?.let { Duration.between(it, now) }
                    if (sinceLastBolus != null && sinceLastBolus < CORRECTION_LOCKOUT) {
                        notes += BolusNote.CORRECTION_BLOCKED_RECENT_BOLUS
                    } else {
                        var positive = raw
                        if (insulinOnBoard > 0) {
                            positive = (positive - insulinOnBoard).coerceAtLeast(0.0)
                            notes += BolusNote.INSULIN_ON_BOARD_SUBTRACTED
                        }
                        if (hadHeavyLiftingRecently(input.recentWorkouts, now)) {
                            positive /= 2
                            notes += BolusNote.HALVED_AFTER_WORKOUT
                        }
                        correction = positive
                    }
                }
            }
        }

        val total = DoseMath.roundToWholeUnit((mealUnits + correction).coerceAtLeast(0.0))
        if (total > LARGE_DOSE_UNITS) notes += BolusNote.LARGE_DOSE
        return BolusResult.Suggestion(mealUnits, correction, insulinOnBoard, total, notes, lastNovorapidAt)
    }

    /** Heavy lifting that is ongoing or ended within [WORKOUT_WINDOW]. */
    fun hadHeavyLiftingRecently(workouts: List<Workout>, now: Instant): Boolean =
        workouts.any {
            it.intensity == WorkoutIntensity.HEAVY_LIFTING &&
                !it.startedAt.isAfter(now) &&
                now.isBefore(it.endedAt.plus(WORKOUT_WINDOW))
        }
}
