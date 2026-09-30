package com.example.healthtracker.ui.timeline

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.TimerTrigger
import com.example.healthtracker.testutil.FakeCorrectionCheckScheduler
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.FakePostMealTimerScheduler
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.FakeSleepRepository
import com.example.healthtracker.testutil.FakeWorkoutRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // UTC+4, so local-day boundaries differ from UTC ones.
    private val zone = ZoneId.of("Asia/Dubai")
    private val clock = MutableClock(Instant.parse("2026-09-29T12:00:00Z"), zone)
    private val meals = FakeMealRepository()
    private val insulin = FakeInsulinRepository()
    private val glucose = FakeGlucoseRepository()
    private val settings = FakeSettingsRepository()
    private val workouts = FakeWorkoutRepository()
    private val sleep = FakeSleepRepository()
    private val timers = FakePostMealTimerScheduler()
    private val correctionChecks = FakeCorrectionCheckScheduler()
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy {
        TimelineViewModel(meals, insulin, glucose, workouts, sleep, settings, timers, correctionChecks, clock)
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private val state get() = viewModel.uiState.value

    private fun at(localDateTime: String) = LocalDateTime.parse(localDateTime).atZone(zone).toInstant()

    @Test
    fun mergesTodaysEntriesOldestFirst_withSummary() = runTest {
        meals.meals.value = listOf(
            Meal(1, "breakfast", Macros(400, 20, 50, 10), at("2026-09-29T08:00")),
            Meal(2, "lunch", Macros(600, 40, 60, 20), at("2026-09-29T13:00")),
            Meal(3, "yesterday", Macros(999, 1, 1, 1), at("2026-09-28T23:59")),
        )
        insulin.doses.value = listOf(
            InsulinDose(1, InsulinType.TRESIBA, 20.0, at("2026-09-29T07:00")),
            InsulinDose(2, InsulinType.NOVORAPID, 4.5, at("2026-09-29T07:55")),
            InsulinDose(3, InsulinType.NOVORAPID, 6.0, at("2026-09-29T12:55")),
        )
        glucose.readings.value = listOf(
            GlucoseReading(1, 100, GlucoseContext.PRE_MEAL, at("2026-09-29T07:50")),
            GlucoseReading(2, 161, GlucoseContext.POST_MEAL, at("2026-09-29T10:00")),
            GlucoseReading(3, 300, GlucoseContext.OTHER, at("2026-09-30T00:00")), // tomorrow, local
        )
        subscribe()

        assertThat(state.isLoading).isFalse()
        assertThat(state.isToday).isTrue()
        assertThat(state.date).isEqualTo(LocalDate.of(2026, 9, 29))
        assertThat(state.entries.map { it.key }).containsExactly(
            "insulin-1", "glucose-1", "insulin-2", "meal-1", "glucose-2", "insulin-3", "meal-2",
        ).inOrder()
        assertThat(state.summary).isEqualTo(
            DaySummary(
                macros = Macros(1_000, 60, 110, 30),
                novorapidUnits = 10.5,
                tresibaUnits = 20.0,
                readingCount = 2,
                averageGlucoseMgDl = 131,
            ),
        )
    }

    @Test
    fun dayNavigation_cannotGoPastToday() = runTest {
        meals.meals.value = listOf(Meal(1, "yesterday", Macros(500, 1, 1, 1), at("2026-09-28T19:00")))
        subscribe()

        viewModel.nextDay()
        assertThat(state.date).isEqualTo(LocalDate.of(2026, 9, 29))

        viewModel.previousDay()
        assertThat(state.date).isEqualTo(LocalDate.of(2026, 9, 28))
        assertThat(state.isToday).isFalse()
        assertThat(state.entries.map { it.key }).containsExactly("meal-1")

        viewModel.goToToday()
        assertThat(state.entries).isEmpty()
    }

    @Test
    fun liveUpdates_andDelete() = runTest {
        subscribe()
        assertThat(state.entries).isEmpty()
        assertThat(state.summary.averageGlucoseMgDl).isNull()

        glucose.log(GlucoseReading(mgDl = 110, context = GlucoseContext.PRE_MEAL, measuredAt = clock.now))
        assertThat(state.entries).hasSize(1)

        viewModel.delete(state.entries.single())
        assertThat(state.entries).isEmpty()
    }

    @Test
    fun followsGlobalGlucoseUnit() = runTest {
        subscribe()
        assertThat(state.glucoseUnit).isEqualTo(GlucoseUnit.MG_DL)
        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        assertThat(state.glucoseUnit).isEqualTo(GlucoseUnit.MMOL_L)
    }

    @Test
    fun sleep_framesTheDay_withNapsInTheFeed() = runTest {
        sleep.sessions.value = listOf(
            SleepSession(1, bedtime = at("2026-09-28T23:30"), wakeTime = at("2026-09-29T07:00")),
            SleepSession(2, bedtime = at("2026-09-29T14:00"), wakeTime = at("2026-09-29T14:40")),
            SleepSession(3, bedtime = at("2026-09-29T23:15"), wakeTime = at("2026-09-30T06:45")),
        )
        subscribe()

        assertThat(state.wake?.id).isEqualTo(1)
        assertThat(state.bedtime?.id).isEqualTo(3)
        assertThat(state.entries.map { it.key }).containsExactly("sleep-2")

        viewModel.delete(state.entries.single())
        assertThat(sleep.sessions.value.map { it.id }).containsExactly(1L, 3L)
    }

    @Test
    fun todayShowsLatestGlucoseAndReminders_pastDaysDoNot() = runTest {
        glucose.log(GlucoseReading(mgDl = 190, context = GlucoseContext.OTHER, measuredAt = at("2026-09-29T11:00")))
        timers.schedule(PostMealTimer(at("2026-09-29T14:00"), TimerTrigger.NOVORAPID_DOSE, sourceId = 1))
        correctionChecks.schedule(CorrectionCheck(doseId = 2, dosedAt = at("2026-09-29T12:00")))
        subscribe()

        assertThat(state.latestGlucose?.mgDl).isEqualTo(190)
        assertThat(state.activeTimer).isNotNull()
        assertThat(state.correctionCheck?.fireAt).isEqualTo(at("2026-09-29T15:00"))

        viewModel.cancelCorrectionCheck()
        assertThat(state.correctionCheck).isNull()

        viewModel.previousDay()
        assertThat(state.latestGlucose).isNull()
        assertThat(state.activeTimer).isNull()
    }

    @Test
    fun weeklyReview_onlyOnSundays() = runTest {
        subscribe()
        assertThat(state.showWeeklyReview).isFalse() // Tuesday

        clock.now = at("2026-10-04T09:00") // Sunday
        viewModel.goToToday()
        assertThat(state.showWeeklyReview).isTrue()
    }

    @Test
    fun ringsFollowTheDailyGoals() = runTest {
        subscribe()
        assertThat(state.goals).isEqualTo(SettingsRepository.DEFAULT_DAILY_GOALS)
        settings.setDailyGoals(Macros(1800, 120, 150, 60))
        assertThat(state.goals.carbsGrams).isEqualTo(150)
    }
}
