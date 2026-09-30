package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.model.WorkoutIntensity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BolusCalculatorTest {

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val profile = TherapyProfile(carbRatio = 12.0, correctionFactor = 25.0, targetMgDl = 140)

    private fun calc(
        carbs: Int? = 60,
        glucose: Int? = null,
        glucoseAge: Duration = Duration.ofMinutes(2),
        doses: List<InsulinDose> = emptyList(),
        workouts: List<Workout> = emptyList(),
        profile: TherapyProfile = this.profile,
    ) = BolusCalculator.calculate(
        BolusInput(carbs, glucose, glucose?.let { now.minus(glucoseAge) }, profile, doses, workouts, now),
    )

    private fun suggestion(result: BolusResult) = result as BolusResult.Suggestion

    @Test
    fun mealOnly_withoutGlucose() {
        val s = suggestion(calc(carbs = 60))
        assertThat(s.mealUnits).isEqualTo(5.0)
        assertThat(s.totalUnits).isEqualTo(5.0)
        assertThat(s.notes).containsExactly(BolusNote.NO_GLUCOSE)
    }

    @Test
    fun dose_roundsToTheNearestWholeUnit() {
        assertThat(suggestion(calc(carbs = 50)).totalUnits).isEqualTo(4.0) // 4.17
        assertThat(suggestion(calc(carbs = 56)).totalUnits).isEqualTo(5.0) // 4.67
        assertThat(suggestion(calc(carbs = 54)).totalUnits).isEqualTo(5.0) // 4.5
    }

    @Test
    fun correction_followsTheFormula() {
        // (215 - 140) / 25 = 3 u, plus 60 g / 12 = 5 u
        val s = suggestion(calc(glucose = 215))
        assertThat(s.correctionUnits).isEqualTo(3.0)
        assertThat(s.totalUnits).isEqualTo(8.0)
        assertThat(s.notes).isEmpty()
    }

    @Test
    fun belowTarget_reducesMealDose_evenRightAfterABolus() {
        val s = suggestion(calc(glucose = 115, doses = listOf(novorapid(2.0, now.minus(Duration.ofHours(1))))))
        assertThat(s.correctionUnits).isEqualTo(-1.0)
        assertThat(s.totalUnits).isEqualTo(4.0)
        assertThat(s.notes).contains(BolusNote.BELOW_TARGET_REDUCED)
    }

    @Test
    fun correctionOnlyBelowTarget_isZeroNotNegative() {
        assertThat(suggestion(calc(carbs = null, glucose = 120)).totalUnits).isEqualTo(0.0)
    }

    @Test
    fun belowSeventy_meansTreatTheLow() {
        assertThat(calc(glucose = 65)).isEqualTo(BolusResult.TreatLow(65))
    }

    @Test
    fun staleReading_isNotCorrectedFrom() {
        val s = suggestion(calc(glucose = 250, glucoseAge = Duration.ofMinutes(20)))
        assertThat(s.correctionUnits).isEqualTo(0.0)
        assertThat(s.notes).containsExactly(BolusNote.GLUCOSE_STALE)
    }

    @Test
    fun staleLowReading_doesNotBlockTheCalculator() {
        assertThat(calc(glucose = 60, glucoseAge = Duration.ofHours(2))).isInstanceOf(BolusResult.Suggestion::class.java)
    }

    @Test
    fun noCorrectionWithinThreeHoursOfNovorapid() {
        val s = suggestion(calc(glucose = 240, doses = listOf(novorapid(4.0, now.minus(Duration.ofMinutes(170))))))
        assertThat(s.correctionUnits).isEqualTo(0.0)
        assertThat(s.totalUnits).isEqualTo(5.0) // meal part still given
        assertThat(s.notes).contains(BolusNote.CORRECTION_BLOCKED_RECENT_BOLUS)
    }

    @Test
    fun afterThreeHours_remainingInsulinIsSubtracted() {
        // 4 u given 3.5 h ago → 0.5 u still active; 3 u correction becomes 2.5 u
        val s = suggestion(calc(glucose = 215, doses = listOf(novorapid(4.0, now.minus(Duration.ofMinutes(210))))))
        assertThat(s.insulinOnBoard).isWithin(1e-9).of(0.5)
        assertThat(s.correctionUnits).isWithin(1e-9).of(2.5)
        assertThat(s.totalUnits).isEqualTo(8.0) // 7.5
        assertThat(s.notes).contains(BolusNote.INSULIN_ON_BOARD_SUBTRACTED)
    }

    @Test
    fun heavyLifting_halvesTheCorrectionOnly() {
        val lifting = workout(now.minus(Duration.ofHours(3)), minutes = 60)
        val s = suggestion(calc(glucose = 215, workouts = listOf(lifting)))
        assertThat(s.correctionUnits).isEqualTo(1.5)
        assertThat(s.totalUnits).isEqualTo(7.0) // 6.5
        assertThat(s.notes).contains(BolusNote.HALVED_AFTER_WORKOUT)

        val cardio = workout(now.minus(Duration.ofHours(3)), intensity = WorkoutIntensity.CARDIO)
        assertThat(suggestion(calc(glucose = 215, workouts = listOf(cardio))).correctionUnits).isEqualTo(3.0)

        val longAgo = workout(now.minus(Duration.ofHours(8)), minutes = 60)
        assertThat(suggestion(calc(glucose = 215, workouts = listOf(longAgo))).correctionUnits).isEqualTo(3.0)
    }

    @Test
    fun defaultProfile_correctsOneUnitPer50() {
        // (240 - 140) / 50 = 2 u, plus 60 g / 12 = 5 u
        val s = suggestion(calc(glucose = 240, profile = TherapyProfile()))
        assertThat(s.correctionUnits).isEqualTo(2.0)
        assertThat(s.totalUnits).isEqualTo(7.0)
    }

    @Test
    fun largeDose_isFlagged() {
        assertThat(suggestion(calc(carbs = 200)).notes).contains(BolusNote.LARGE_DOSE)
    }
}
