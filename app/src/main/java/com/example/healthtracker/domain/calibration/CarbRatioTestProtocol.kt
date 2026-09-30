package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Workout
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

enum class PreconditionKind {
    /** No NovoRapid in the last 4 h. */
    NO_ACTIVE_INSULIN,
    /** A reading from the last 15 min between 80 and 180. */
    GLUCOSE_IN_RANGE,
    /** An earlier reading 20–90 min before it, within 30 mg/dL. */
    GLUCOSE_STABLE,
    /** Test on a rest day. */
    NO_WORKOUT_TODAY,
}

enum class PreconditionStatus { OK, FAILED, UNKNOWN }

data class Precondition(val kind: PreconditionKind, val status: PreconditionStatus)

enum class TestOutcome {
    /** Back within ±30 mg/dL of the start by hour 4. */
    GOOD,
    /** Still more than 30 above the start at hour 4: ratio too weak. */
    TOO_WEAK,
    /** More than 30 below the start at hour 4: ratio too strong. */
    TOO_STRONG,
    /** Went below 70 during the test: too strong; the test stops. */
    LOW_DURING_TEST,
    INCOMPLETE,
}

data class TestGrade(val outcome: TestOutcome, val suggestedRatio: Double?)

/**
 * Structured carb-ratio test: on a rest day, with stable glucose and no active insulin, eat
 * exactly 50 g of fast carbs dosed at the ratio under test, and check at 2, 3 and 4 hours.
 */
object CarbRatioTestProtocol {
    const val TEST_CARBS_GRAMS = 50
    const val TOLERANCE_MG_DL = 30
    val START_RANGE = 80..180
    val CHECKPOINTS: List<Duration> = listOf(Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(4))
    private val FRESH: Duration = Duration.ofMinutes(15)
    private val STABLE_EARLIEST: Duration = Duration.ofMinutes(90)
    private val STABLE_LATEST: Duration = Duration.ofMinutes(20)

    fun testDose(ratio: Double): Double = DoseMath.roundToWholeUnit(TEST_CARBS_GRAMS / ratio)

    fun checkPreconditions(
        readings: List<GlucoseReading>,
        doses: List<InsulinDose>,
        workouts: List<Workout>,
        now: Instant,
        zone: ZoneId,
    ): List<Precondition> {
        val lastBolus = InsulinOnBoard.lastNovorapidAt(doses, now)
        val noInsulin = lastBolus == null || Duration.between(lastBolus, now) >= InsulinOnBoard.DURATION

        val latest = readings.filter { !it.measuredAt.isAfter(now) }.maxByOrNull { it.measuredAt }
            ?.takeIf { Duration.between(it.measuredAt, now) <= FRESH }
        val inRange = when {
            latest == null -> PreconditionStatus.UNKNOWN
            latest.mgDl in START_RANGE -> PreconditionStatus.OK
            else -> PreconditionStatus.FAILED
        }
        val earlier = latest?.let { l ->
            readings.filter {
                !it.measuredAt.isBefore(l.measuredAt.minus(STABLE_EARLIEST)) && !it.measuredAt.isAfter(l.measuredAt.minus(STABLE_LATEST))
            }.maxByOrNull { it.measuredAt }
        }
        val stable = when {
            latest == null || earlier == null -> PreconditionStatus.UNKNOWN
            abs(latest.mgDl - earlier.mgDl) <= TOLERANCE_MG_DL -> PreconditionStatus.OK
            else -> PreconditionStatus.FAILED
        }
        val today = now.atZone(zone).toLocalDate()
        val workedOutToday = workouts.any { it.startedAt.atZone(zone).toLocalDate() == today }

        return listOf(
            Precondition(PreconditionKind.NO_ACTIVE_INSULIN, if (noInsulin) PreconditionStatus.OK else PreconditionStatus.FAILED),
            Precondition(PreconditionKind.GLUCOSE_IN_RANGE, inRange),
            Precondition(PreconditionKind.GLUCOSE_STABLE, stable),
            Precondition(PreconditionKind.NO_WORKOUT_TODAY, if (workedOutToday) PreconditionStatus.FAILED else PreconditionStatus.OK),
        )
    }

    fun grade(test: CarbRatioTest): TestGrade {
        val readings = listOfNotNull(test.mgDlAt2h, test.mgDlAt3h, test.mgDlAt4h)
        if (readings.any { it < GlucoseLimits.LOW_MG_DL }) {
            return TestGrade(TestOutcome.LOW_DURING_TEST, DoseMath.weakerRatio(test.ratioTested))
        }
        val final = test.mgDlAt4h ?: return TestGrade(TestOutcome.INCOMPLETE, null)
        val diff = final - test.startMgDl
        return when {
            diff > TOLERANCE_MG_DL -> TestGrade(TestOutcome.TOO_WEAK, DoseMath.strongerRatio(test.ratioTested))
            diff < -TOLERANCE_MG_DL -> TestGrade(TestOutcome.TOO_STRONG, DoseMath.weakerRatio(test.ratioTested))
            else -> TestGrade(TestOutcome.GOOD, test.ratioTested)
        }
    }
}
