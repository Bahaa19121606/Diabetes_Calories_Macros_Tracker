package com.example.healthtracker.domain.review

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.at
import com.example.healthtracker.domain.calibration.CalibrationFixtures.meal
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.calibration.CalibrationFixtures.tresiba
import com.example.healthtracker.domain.calibration.CalibrationFixtures.workout
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.SleepSession
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class PeriodReviewTest {

    private val wednesday = LocalDate.of(2026, 9, 30)
    private val week = ReviewPeriod.WEEK.rangeContaining(wednesday)
    private val monday = LocalDate.of(2026, 9, 28)

    private fun night(wakeDate: LocalDate, hours: Long) = SleepSession(
        bedtime = at(wakeDate, "07:00").minus(Duration.ofHours(hours)),
        wakeTime = at(wakeDate, "07:00"),
    )

    @Test
    fun weeksRunMondayToSunday_andMonthsAreCalendarMonths() {
        assertThat(week).isEqualTo(PeriodRange(monday, LocalDate.of(2026, 10, 5)))
        assertThat(week.lastDay.dayOfWeek).isEqualTo(java.time.DayOfWeek.SUNDAY)
        assertThat(ReviewPeriod.WEEK.shift(week, -1).start).isEqualTo(LocalDate.of(2026, 9, 21))

        val month = ReviewPeriod.MONTH.rangeContaining(wednesday)
        assertThat(month).isEqualTo(PeriodRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
        assertThat(month.days).isEqualTo(30)
        assertThat(ReviewPeriod.MONTH.shift(month, 1).days).isEqualTo(31)
    }

    @Test
    fun daysElapsed_stopsAtToday() {
        assertThat(week.daysElapsed(wednesday)).isEqualTo(3)
        assertThat(week.daysElapsed(LocalDate.of(2026, 11, 1))).isEqualTo(7)
        assertThat(week.daysElapsed(LocalDate.of(2026, 9, 1))).isEqualTo(0)
    }

    @Test
    fun summarizesGlucose() {
        val readings = listOf(
            reading(50, at(monday, "03:00")),
            reading(160, at(monday, "07:00"), GlucoseContext.FASTING),
            reading(100, at(monday, "07:30"), GlucoseContext.FASTING), // second fasting reading: not the morning's first
            reading(200, at(monday.plusDays(1), "13:00")),
            reading(140, at(monday.plusDays(1), "07:00"), GlucoseContext.FASTING),
            reading(400, at(monday.minusDays(1), "13:00")), // last week
        )
        val summary = PeriodReview.summarize(week, wednesday, ZONE, readings, emptyList(), emptyList(), emptyList(), emptyList())
        val glucose = summary.glucose!!

        assertThat(glucose.readings).isEqualTo(5)
        assertThat(glucose.averageMgDl).isEqualTo(130)
        assertThat(glucose.lowPercent).isEqualTo(20)
        assertThat(glucose.highPercent).isEqualTo(20)
        assertThat(glucose.inRangePercent).isEqualTo(60)
        assertThat(glucose.lows).isEqualTo(1)
        assertThat(glucose.lowestMgDl).isEqualTo(50)
        assertThat(glucose.highestMgDl).isEqualTo(200)
        assertThat(glucose.averageFastingMgDl).isEqualTo(150)
        assertThat(summary.daily.map { it.date }).containsExactly(monday, monday.plusDays(1)).inOrder()
        assertThat(summary.daily.first().lowestMgDl).isEqualTo(50)
    }

    @Test
    fun averagesFoodInsulinAndSleep_overTheDaysLogged() {
        val meals = listOf(
            meal(at(monday, "08:00"), carbs = 60, protein = 20, fat = 10),
            meal(at(monday, "13:00"), carbs = 80, protein = 40, fat = 30),
            meal(at(monday.plusDays(1), "13:00"), carbs = 40, protein = 30, fat = 20),
        )
        val doses = listOf(
            novorapid(4.0, at(monday, "08:00")),
            novorapid(6.0, at(monday, "13:00")),
            novorapid(5.0, at(monday.plusDays(1), "13:00")),
            tresiba(20.0, at(monday, "21:00")),
            tresiba(22.0, at(monday.plusDays(1), "21:00")),
        )
        val sleeps = listOf(
            night(monday, 7),
            night(monday.plusDays(1), 5),
            night(monday.minusDays(1), 9), // woke up last week
        )
        val workouts = listOf(workout(at(monday, "18:00"), minutes = 45), workout(at(monday.plusDays(2), "18:00"), minutes = 30))
        val summary = PeriodReview.summarize(week, wednesday, ZONE, emptyList(), meals, doses, sleeps, workouts)

        assertThat(summary.nutrition!!.daysLogged).isEqualTo(2)
        assertThat(summary.nutrition!!.dailyAverage).isEqualTo(Macros(calories = 750, proteinGrams = 45, carbsGrams = 90, fatGrams = 30))
        assertThat(summary.insulin!!.averageDailyNovorapid).isEqualTo(7.5)
        assertThat(summary.insulin!!.averageTresiba).isEqualTo(21.0)
        assertThat(summary.sleep!!.nights).isEqualTo(2)
        assertThat(summary.sleep!!.average).isEqualTo(Duration.ofHours(6))
        assertThat(summary.sleep!!.shortNights).isEqualTo(1)
        assertThat(summary.activity).isEqualTo(ActivitySummary(workouts = 2, totalMinutes = 75))
        assertThat(summary.glucose).isNull()
        assertThat(summary.isEmpty).isFalse()
    }

    @Test
    fun anEmptyPeriod_isEmpty() {
        val summary = PeriodReview.summarize(week, wednesday, ZONE, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertThat(summary.isEmpty).isTrue()
    }
}
