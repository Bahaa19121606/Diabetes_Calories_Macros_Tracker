package com.example.healthtracker.ui.sleep

import com.example.healthtracker.testutil.FakeSleepRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-09-29T09:00:00Z"))
    private val repository = FakeSleepRepository()
    private val viewModel by lazy { SleepViewModel(repository, clock) }
    private val state get() = viewModel.uiState.value

    @Test
    fun defaultsToLastNight_andSavesTheSession() = runTest {
        assertThat(state.wakeDate).isEqualTo(LocalDate.of(2026, 9, 29))
        viewModel.onBedtimeChange(23 * 60 + 30)
        viewModel.onWakeTimeChange(7 * 60)
        assertThat(state.durationMinutes).isEqualTo(7 * 60 + 30)

        viewModel.save()

        val session = repository.sessions.value.single()
        assertThat(session.bedtime).isEqualTo(Instant.parse("2026-09-28T23:30:00Z"))
        assertThat(session.duration).isEqualTo(Duration.ofMinutes(450))
        assertThat(state.saved).isEqualTo(session)
    }

    @Test
    fun beforeTheUsualWakeTime_defaultsToTheNightBefore() = runTest {
        clock.now = Instant.parse("2026-09-29T04:00:00Z")
        assertThat(state.wakeDate).isEqualTo(LocalDate.of(2026, 9, 28))
    }

    @Test
    fun rejectsFutureWakeTimesAndZeroLength() = runTest {
        viewModel.onWakeTimeChange(10 * 60) // 10:00 today, but it's 09:00
        viewModel.save()
        assertThat(state.error).isEqualTo(SleepError.WAKE_IN_FUTURE)

        viewModel.onWakeDateChange(LocalDate.of(2026, 9, 28))
        viewModel.onBedtimeChange(10 * 60)
        viewModel.save()
        assertThat(state.error).isEqualTo(SleepError.ZERO_LENGTH)
        assertThat(repository.sessions.value).isEmpty()
    }

    @Test
    fun rejectsImplausiblyLongSleep() = runTest {
        viewModel.onBedtimeChange(7 * 60 + 5) // 07:05 → 07:00 next day: 23 h 55 min
        viewModel.onWakeTimeChange(7 * 60)
        viewModel.save()
        assertThat(state.error).isEqualTo(SleepError.TOO_LONG)
    }
}
