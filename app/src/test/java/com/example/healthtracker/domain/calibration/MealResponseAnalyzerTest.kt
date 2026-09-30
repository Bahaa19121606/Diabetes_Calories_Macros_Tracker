package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.meal
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MealResponseAnalyzerTest {

    private val now = Instant.parse("2026-09-29T20:00:00Z")

    private class Log {
        val doses = mutableListOf<InsulinDose>()
        val meals = mutableListOf<Meal>()
        val readings = mutableListOf<GlucoseReading>()
        val workouts = mutableListOf<Workout>()
    }

    /** A calculator-dosed meal at [at]: pre-meal reading 10 min before, 2-hour reading after. */
    private fun Log.dosedMeal(
        at: Instant,
        pre: Int = 120,
        post: Int? = 150,
        ratio: Double = 12.0,
        fat: Int = 15,
    ) {
        val m = meal(at, fat = fat).also { meals += it }
        doses += novorapid(5.0, at, carbs = 60, ratio = ratio, mealId = m.id)
        readings += reading(pre, at.minus(Duration.ofMinutes(10)), GlucoseContext.PRE_MEAL)
        post?.let { readings += reading(it, at.plus(Duration.ofHours(2)), GlucoseContext.POST_MEAL) }
    }

    private fun daysAgo(days: Long, hours: Long = 0) = now.minus(Duration.ofDays(days)).minus(Duration.ofHours(hours))

    private fun Log.analyze(ratio: Double = 12.0) =
        MealResponseAnalyzer.analyze(ratio, doses, meals, readings, workouts, now)

    @Test
    fun consistentHighRises_afterSeventyTwoHours_suggestStrongerRatio() {
        val log = Log().apply {
            dosedMeal(daysAgo(4), pre = 120, post = 200)
            dosedMeal(daysAgo(3), pre = 110, post = 185)
            dosedMeal(daysAgo(1), pre = 130, post = 150)
        }
        assertThat(log.analyze().recommendation).isEqualTo(CarbRatioRecommendation.Stronger(12.0, 11.0))
    }

    @Test
    fun consistentDrops_suggestWeakerRatio() {
        val log = Log().apply {
            dosedMeal(daysAgo(4), pre = 160, post = 110)
            dosedMeal(daysAgo(3), pre = 150, post = 105)
            dosedMeal(daysAgo(1), pre = 130, post = 125)
        }
        assertThat(log.analyze().recommendation).isEqualTo(CarbRatioRecommendation.Weaker(12.0, 13.0))
    }

    @Test
    fun modestRises_hold() {
        val log = Log().apply {
            dosedMeal(daysAgo(4), pre = 120, post = 160)
            dosedMeal(daysAgo(3), pre = 110, post = 185)
            dosedMeal(daysAgo(1), pre = 130, post = 150)
        }
        assertThat(log.analyze().recommendation).isEqualTo(CarbRatioRecommendation.Hold(12.0))
    }

    @Test
    fun beforeSeventyTwoHours_waits() {
        val first = daysAgo(2)
        val log = Log().apply {
            dosedMeal(first, post = 200)
            dosedMeal(daysAgo(1), post = 200)
            dosedMeal(daysAgo(0, hours = 3), post = 200)
        }
        assertThat(log.analyze().recommendation)
            .isEqualTo(CarbRatioRecommendation.NeedMoreTime(12.0, first.plus(Duration.ofHours(72))))
    }

    @Test
    fun postMealLow_suggestsWeakerImmediately() {
        val log = Log().apply { dosedMeal(daysAgo(0, hours = 3), pre = 110, post = 65) }
        assertThat(log.analyze().recommendation).isEqualTo(CarbRatioRecommendation.WeakerForLows(12.0, 13.0))
    }

    @Test
    fun needsThreeQualifyingMeals() {
        val log = Log().apply {
            dosedMeal(daysAgo(4), post = 200)
            dosedMeal(daysAgo(3), post = 200)
            dosedMeal(daysAgo(2), pre = 220, post = 260) // pre out of range
        }
        assertThat(log.analyze().recommendation).isEqualTo(CarbRatioRecommendation.NeedMoreMeals(12.0, 2))
    }

    @Test
    fun exclusions() {
        val log = Log().apply {
            dosedMeal(daysAgo(5), ratio = 10.0)
            dosedMeal(daysAgo(4), pre = 200)
            dosedMeal(daysAgo(3), fat = 45)
            dosedMeal(daysAgo(2), post = null)
            dosedMeal(daysAgo(1))
            doses += novorapid(2.0, daysAgo(1).plus(Duration.ofHours(1))) // correction before the 2-hour check
            dosedMeal(daysAgo(0, hours = 4))
            workouts += workout(daysAgo(0, hours = 7))
        }
        val exclusions = log.analyze().responses.map { it.exclusion }
        assertThat(exclusions).containsExactly(
            MealExclusion.WORKOUT,
            MealExclusion.EXTRA_INSULIN_OR_FOOD,
            MealExclusion.NO_POST_MEAL_READING,
            MealExclusion.HIGH_FAT_OR_PROTEIN,
            MealExclusion.PRE_MEAL_OUT_OF_RANGE,
            MealExclusion.DIFFERENT_RATIO,
        ).inOrder()
    }

    @Test
    fun manualDosesWithoutCalculatorData_areIgnored() {
        val log = Log().apply { doses += novorapid(5.0, daysAgo(1)) }
        assertThat(log.analyze().responses).isEmpty()
    }
}
