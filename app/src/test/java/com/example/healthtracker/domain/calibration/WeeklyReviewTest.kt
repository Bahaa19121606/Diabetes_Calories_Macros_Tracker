package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.at
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.SleepSession
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class WeeklyReviewTest {

    private val today = LocalDate.of(2026, 10, 4) // a Sunday
    private val now = at(today, "09:00")

    private fun night(daysAgo: Long, hours: Long) = SleepSession(
        bedtime = at(today.minusDays(daysAgo), "07:00").minus(Duration.ofHours(hours)),
        wakeTime = at(today.minusDays(daysAgo), "07:00"),
    )

    @Test
    fun averagesTheFirstFastingReadingOfEachMorning_andSleep() {
        val readings = listOf(
            reading(160, at(today, "07:05"), GlucoseContext.FASTING),
            reading(100, at(today, "08:30"), GlucoseContext.FASTING), // second reading that morning: ignored
            reading(170, at(today.minusDays(1), "07:10"), GlucoseContext.FASTING),
            reading(250, at(today.minusDays(1), "13:00"), GlucoseContext.POST_MEAL),
            reading(90, at(today.minusDays(9), "07:00"), GlucoseContext.FASTING), // outside the week
        )
        val context = WeeklyReview.context(readings, listOf(night(0, 5), night(1, 6), night(2, 4)), now, ZONE)

        assertThat(context.averageFastingMgDl).isEqualTo(165)
        assertThat(context.fastingDays).isEqualTo(2)
        assertThat(context.averageSleep).isEqualTo(Duration.ofHours(5))
        assertThat(context.sleepIsShort).isTrue()
    }

    @Test
    fun shortSleep_putsRestBeforeAnIncrease_only() {
        val short = WeeklyContext(165, 5, Duration.ofHours(5), nightsLogged = 4)
        val increase = TresibaRecommendation.Increase(42.0, 2, 4)
        assertThat(WeeklyReview.sleepBeforeIncrease(short, increase)).isTrue()
        assertThat(WeeklyReview.sleepBeforeIncrease(short, TresibaRecommendation.Reduce(42.0, 38.0))).isFalse()

        val fewNights = short.copy(nightsLogged = 2)
        assertThat(WeeklyReview.sleepBeforeIncrease(fewNights, increase)).isFalse()
        val enoughSleep = short.copy(averageSleep = Duration.ofHours(7))
        assertThat(WeeklyReview.sleepBeforeIncrease(enoughSleep, increase)).isFalse()
    }

    @Test
    fun reviewDayIsSunday() {
        assertThat(WeeklyReview.isReviewDay(today)).isTrue()
        assertThat(WeeklyReview.isReviewDay(today.minusDays(1))).isFalse()
    }
}
