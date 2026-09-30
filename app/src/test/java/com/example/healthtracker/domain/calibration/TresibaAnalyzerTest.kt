package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.at
import com.example.healthtracker.domain.calibration.CalibrationFixtures.meal
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.calibration.CalibrationFixtures.tresiba
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TresibaAnalyzerTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val now = at(today, "09:00")

    /** Bedtime 23:00 the evening before [morning], fasting 07:00 on [morning]. */
    private fun night(morning: LocalDate, bedtime: Int, fasting: Int) = listOf(
        reading(bedtime, at(morning.minusDays(1), "23:00"), GlucoseContext.BEDTIME),
        reading(fasting, at(morning, "07:00"), GlucoseContext.FASTING),
    )

    private fun lastThreeNights(bedtime: Int, fasting: Int) =
        (0L..2L).flatMap { night(today.minusDays(it), bedtime, fasting) }

    private val steadyDose = listOf(tresiba(42.0, at(today.minusDays(20), "22:00")), tresiba(42.0, at(today.minusDays(1), "22:00")))

    private fun analyze(
        readings: List<GlucoseReading>,
        doses: List<InsulinDose> = steadyDose,
        meals: List<Meal> = emptyList(),
        workouts: List<Workout> = emptyList(),
    ) = TresibaAnalyzer.analyze(readings, doses, meals, workouts, now, ZONE)

    @Test
    fun droppingThreeCleanNights_reducesByTenPercent() {
        val report = analyze(lastThreeNights(bedtime = 160, fasting = 110))
        assertThat(report.nights.map { it.trend }.distinct()).containsExactly(OvernightTrend.DROPPING)
        assertThat(report.recommendation).isEqualTo(TresibaRecommendation.Reduce(42.0, 38.0))
    }

    @Test
    fun risingThreeCleanNights_increasesByTwoToFour() {
        assertThat(analyze(lastThreeNights(bedtime = 110, fasting = 170)).recommendation)
            .isEqualTo(TresibaRecommendation.Increase(42.0, 2, 4))
    }

    @Test
    fun stableOrMixedNights_hold() {
        assertThat(analyze(lastThreeNights(bedtime = 130, fasting = 120)).recommendation).isEqualTo(TresibaRecommendation.Hold(42.0))
        val mixed = night(today, 160, 110) + night(today.minusDays(1), 110, 170) + night(today.minusDays(2), 160, 110)
        assertThat(analyze(mixed).recommendation).isEqualTo(TresibaRecommendation.Hold(42.0))
    }

    @Test
    fun ruleOfThree_needsThreeConsecutiveRecentNights() {
        assertThat(analyze(night(today, 160, 110) + night(today.minusDays(1), 160, 110)).recommendation)
            .isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 2))

        val gap = night(today, 160, 110) + night(today.minusDays(1), 160, 110) + night(today.minusDays(3), 160, 110)
        assertThat(analyze(gap).recommendation).isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 2))

        val stale = (3L..5L).flatMap { night(today.minusDays(it), 160, 110) }
        assertThat(analyze(stale).recommendation).isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 0))
    }

    @Test
    fun bolusOrFoodBeforeBed_isNotABasalTest() {
        val readings = lastThreeNights(160, 110)
        val lateBolus = novorapid(3.0, at(today.minusDays(1), "21:00"))
        val report = analyze(readings, doses = steadyDose + lateBolus)
        assertThat(report.nights.first().exclusion).isEqualTo(NightExclusion.BOLUS_BEFORE_BED)
        // The two clean nights before it still form a run...
        assertThat(report.recommendation).isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 2))
        // ...but an excluded night in the middle breaks it.
        val middleBolus = novorapid(3.0, at(today.minusDays(2), "21:00"))
        assertThat(analyze(readings, doses = steadyDose + middleBolus).recommendation)
            .isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 1))

        val lateSnack = meal(at(today.minusDays(2), "22:00"))
        assertThat(analyze(readings, meals = listOf(lateSnack)).nights[1].exclusion).isEqualTo(NightExclusion.FOOD_BEFORE_BED)
    }

    @Test
    fun workoutDay_isExcluded() {
        val gym = workout(at(today.minusDays(1), "18:00"))
        val report = analyze(lastThreeNights(160, 110), workouts = listOf(gym))
        assertThat(report.nights.first().exclusion).isEqualTo(NightExclusion.WORKOUT_DAY)
        assertThat(report.recommendation).isInstanceOf(TresibaRecommendation.NeedMoreData::class.java)
    }

    @Test
    fun recentDoseChange_waitsFourDays_andIgnoresOlderNights() {
        val changed = listOf(tresiba(42.0, at(today.minusDays(10), "22:00")), tresiba(38.0, at(today.minusDays(2), "22:00")))
        val report = analyze(lastThreeNights(160, 110), doses = changed)
        assertThat(report.currentDose).isEqualTo(38.0)
        assertThat(report.recommendation).isEqualTo(TresibaRecommendation.Wait(38.0, at(today.plusDays(2), "22:00")))
        assertThat(report.nights.last().exclusion).isEqualTo(NightExclusion.PREVIOUS_DOSE)
    }

    @Test
    fun twoNightsWithLows_reduceImmediately_evenDuringTheWait() {
        val changed = listOf(tresiba(42.0, at(today.minusDays(10), "22:00")), tresiba(42.0, at(today.minusDays(3), "22:00")))
        val lows = night(today, 110, 65) + night(today.minusDays(1), 105, 66)
        assertThat(analyze(lows, doses = changed).recommendation)
            .isEqualTo(TresibaRecommendation.ReduceForLows(42.0, 38.0, veryLow = false))
    }

    @Test
    fun oneVeryLowReadingOvernight_reducesImmediately() {
        val readings = night(today, 120, 95) + reading(50, at(today, "03:00"))
        assertThat(analyze(readings).recommendation).isEqualTo(TresibaRecommendation.ReduceForLows(42.0, 38.0, veryLow = true))
    }

    @Test
    fun noTresibaLogged() {
        assertThat(analyze(lastThreeNights(160, 110), doses = emptyList()).recommendation)
            .isEqualTo(TresibaRecommendation.NoDoseLogged)
    }
}
