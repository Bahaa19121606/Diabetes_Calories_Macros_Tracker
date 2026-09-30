package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Workout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CarbRatioTestProtocolTest {

    private val now = Instant.parse("2026-09-29T14:00:00Z")
    private fun minutesAgo(m: Long) = now.minus(Duration.ofMinutes(m))

    private val stableReadings = listOf(reading(110, minutesAgo(45)), reading(120, minutesAgo(5)))

    private fun check(
        readings: List<GlucoseReading> = stableReadings,
        doses: List<InsulinDose> = emptyList(),
        workouts: List<Workout> = emptyList(),
    ) = CarbRatioTestProtocol.checkPreconditions(readings, doses, workouts, now, ZONE).associate { it.kind to it.status }

    @Test
    fun allPreconditionsMet() {
        assertThat(check().values.toSet()).containsExactly(PreconditionStatus.OK)
    }

    @Test
    fun activeInsulin_failsTheTest() {
        assertThat(check(doses = listOf(novorapid(3.0, minutesAgo(200))))[PreconditionKind.NO_ACTIVE_INSULIN])
            .isEqualTo(PreconditionStatus.FAILED)
        assertThat(check(doses = listOf(novorapid(3.0, minutesAgo(250))))[PreconditionKind.NO_ACTIVE_INSULIN])
            .isEqualTo(PreconditionStatus.OK)
    }

    @Test
    fun glucoseChecks() {
        assertThat(check(readings = listOf(reading(120, minutesAgo(30))))[PreconditionKind.GLUCOSE_IN_RANGE])
            .isEqualTo(PreconditionStatus.UNKNOWN)
        assertThat(check(readings = listOf(reading(110, minutesAgo(45)), reading(200, minutesAgo(5))))[PreconditionKind.GLUCOSE_IN_RANGE])
            .isEqualTo(PreconditionStatus.FAILED)
        assertThat(check(readings = listOf(reading(120, minutesAgo(5))))[PreconditionKind.GLUCOSE_STABLE])
            .isEqualTo(PreconditionStatus.UNKNOWN)
        assertThat(check(readings = listOf(reading(170, minutesAgo(45)), reading(120, minutesAgo(5))))[PreconditionKind.GLUCOSE_STABLE])
            .isEqualTo(PreconditionStatus.FAILED)
    }

    @Test
    fun workoutToday_failsTheTest() {
        assertThat(check(workouts = listOf(workout(now.minus(Duration.ofHours(5)))))[PreconditionKind.NO_WORKOUT_TODAY])
            .isEqualTo(PreconditionStatus.FAILED)
    }

    @Test
    fun testDose_isFiftyGramsAtTheTestedRatio() {
        assertThat(CarbRatioTestProtocol.testDose(12.0)).isEqualTo(4.0)
        assertThat(CarbRatioTestProtocol.testDose(7.0)).isEqualTo(7.0)
        assertThat(CarbRatioTestProtocol.testDose(10.0)).isEqualTo(5.0)
    }

    private fun test(r2: Int? = 160, r3: Int? = 140, r4: Int?) = CarbRatioTest(
        startedAt = now, startMgDl = 120, carbsGrams = 50, ratioTested = 12.0, doseUnits = 4.0,
        mgDlAt2h = r2, mgDlAt3h = r3, mgDlAt4h = r4,
    )

    @Test
    fun grading_usesPlusMinusThirty() {
        assertThat(CarbRatioTestProtocol.grade(test(r4 = 150))).isEqualTo(TestGrade(TestOutcome.GOOD, 12.0))
        assertThat(CarbRatioTestProtocol.grade(test(r4 = 90))).isEqualTo(TestGrade(TestOutcome.GOOD, 12.0))
        assertThat(CarbRatioTestProtocol.grade(test(r4 = 151))).isEqualTo(TestGrade(TestOutcome.TOO_WEAK, 11.0))
        assertThat(CarbRatioTestProtocol.grade(test(r4 = 89))).isEqualTo(TestGrade(TestOutcome.TOO_STRONG, 13.0))
    }

    @Test
    fun lowDuringTest_endsItAsTooStrong() {
        assertThat(CarbRatioTestProtocol.grade(test(r3 = 65, r4 = null)))
            .isEqualTo(TestGrade(TestOutcome.LOW_DURING_TEST, 13.0))
    }

    @Test
    fun missingFourHourReading_isIncomplete() {
        assertThat(CarbRatioTestProtocol.grade(test(r4 = null)).outcome).isEqualTo(TestOutcome.INCOMPLETE)
    }
}
