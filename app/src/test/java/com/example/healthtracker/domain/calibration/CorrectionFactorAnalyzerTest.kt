package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.meal
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CorrectionFactorAnalyzerTest {

    private val now = Instant.parse("2026-09-29T20:00:00Z")

    private class Log {
        val doses = mutableListOf<InsulinDose>()
        val meals = mutableListOf<Meal>()
        val readings = mutableListOf<GlucoseReading>()
        val workouts = mutableListOf<Workout>()
    }

    /** A calculator correction of [units] at [at]: a reading 5 min before, another 3 h after. */
    private fun Log.correction(at: Instant, units: Double = 2.0, before: Int = 240, after: Int? = 140, factor: Double = 50.0) {
        doses += novorapid(units, at, carbs = 0, correction = units, factor = factor)
        readings += reading(before, at.minus(Duration.ofMinutes(5)))
        after?.let { readings += reading(it, at.plus(Duration.ofHours(3))) }
    }

    private fun daysAgo(days: Long, hour: Long = 10) = now.minus(Duration.ofDays(days)).minus(Duration.ofHours(20 - hour))

    private fun Log.analyze(factor: Double = 50.0) =
        CorrectionFactorAnalyzer.analyze(factor, doses, meals, readings, workouts, now)

    private fun threeDaysOf(after: Int, units: Double = 2.0) = Log().apply {
        (1L..4L).forEach { correction(daysAgo(it), units = units, after = after) }
    }

    @Test
    fun matchingDrops_hold() {
        // 240 → 140 on 2 u = 50 per unit
        val rec = threeDaysOf(after = 140).analyze().recommendation
        assertThat(rec).isEqualTo(CorrectionFactorRecommendation.Hold(50.0, 50.0))
    }

    @Test
    fun smallerDrops_suggestAStrongerFactor_cappedAt20Percent() {
        // 240 → 180 on 2 u = 30 per unit, capped to 40
        assertThat(threeDaysOf(after = 180).analyze().recommendation)
            .isEqualTo(CorrectionFactorRecommendation.Stronger(50.0, 40.0, 30.0))
    }

    @Test
    fun biggerDrops_suggestAWeakerFactor() {
        // 240 → 110 on 2 u = 65 per unit, capped to 60
        assertThat(threeDaysOf(after = 110).analyze().recommendation)
            .isEqualTo(CorrectionFactorRecommendation.Weaker(50.0, 60.0, 65.0))
        // Exactly 20% off (60 per unit) is within tolerance.
        assertThat(threeDaysOf(after = 120).analyze().recommendation)
            .isEqualTo(CorrectionFactorRecommendation.Hold(50.0, 60.0))
    }

    @Test
    fun waitsFor72HoursAndThreeCorrections() {
        val recent = Log().apply { (0L..2L).forEach { correction(daysAgo(it), after = 180) } }
        assertThat(recent.analyze().recommendation).isInstanceOf(CorrectionFactorRecommendation.NeedMoreTime::class.java)

        val few = Log().apply {
            correction(daysAgo(4), after = 180)
            correction(daysAgo(1), after = 180)
        }
        assertThat(few.analyze().recommendation).isEqualTo(CorrectionFactorRecommendation.NeedMoreCorrections(50.0, 2))
    }

    @Test
    fun aLowAfterACorrection_suggestsWeakerStraightAway() {
        val log = Log().apply { correction(daysAgo(0, hour = 12), after = 65) }
        assertThat(log.analyze().recommendation).isEqualTo(CorrectionFactorRecommendation.WeakerForLows(50.0, 60.0))
    }

    @Test
    fun confoundedCorrections_areNotCounted() {
        val log = threeDaysOf(after = 180)
        val food = daysAgo(1)
        log.meals += meal(food.plus(Duration.ofHours(1)))
        log.doses += novorapid(3.0, daysAgo(2).minus(Duration.ofHours(2)))
        log.workouts += workout(daysAgo(3).plus(Duration.ofHours(1)))
        log.correction(daysAgo(4, hour = 16), after = null)
        log.correction(daysAgo(5), factor = 40.0)

        val exclusions = log.analyze().responses.map { it.exclusion }
        assertThat(exclusions).containsAtLeast(
            CorrectionExclusion.FOOD,
            CorrectionExclusion.OTHER_INSULIN,
            CorrectionExclusion.WORKOUT,
            CorrectionExclusion.NO_FOLLOW_UP_READING,
            CorrectionExclusion.DIFFERENT_FACTOR,
        )
        assertThat(log.analyze().recommendation).isEqualTo(CorrectionFactorRecommendation.NeedMoreCorrections(50.0, 1))
    }

    @Test
    fun mealDoses_areIgnored() {
        val log = Log().apply { doses += novorapid(5.0, daysAgo(1), carbs = 60, correction = 1.0, factor = 50.0) }
        assertThat(log.analyze().responses).isEmpty()
    }

    @Test
    fun step_roundsToFiveAndAlwaysMoves() {
        assertThat(CorrectionFactorAnalyzer.step(50.0, 53.0)).isEqualTo(55.0)
        assertThat(CorrectionFactorAnalyzer.step(50.0, 47.0)).isEqualTo(45.0)
        assertThat(CorrectionFactorAnalyzer.step(50.0, 200.0)).isEqualTo(60.0)
    }
}
