package com.example.healthtracker.ui.calibration

import com.example.healthtracker.domain.calibration.TresibaRecommendation
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.testutil.FakeCarbRatioTestRepository
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.FakeSleepRepository
import com.example.healthtracker.testutil.FakeTherapyProfileRepository
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val glucose = FakeGlucoseRepository()
    private val insulin = FakeInsulinRepository()
    private val profile = FakeTherapyProfileRepository()

    private val viewModel by lazy {
        CalibrationViewModel(
            glucose, insulin, FakeMealRepository(), FakeWorkoutRepository(), FakeSleepRepository(), FakeCarbRatioTestRepository(),
            profile, FakeSettingsRepository(), MutableClock(now),
        )
    }
    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun coverage_showsTodaysChecks() = runTest {
        glucose.log(GlucoseReading(mgDl = 100, context = GlucoseContext.FASTING, measuredAt = now.minus(Duration.ofHours(5))))
        glucose.log(GlucoseReading(mgDl = 120, context = GlucoseContext.PRE_MEAL, measuredAt = now.minus(Duration.ofHours(1))))
        subscribe()

        assertThat(state.coverage.map { it.date }).containsExactly(
            LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 27),
        ).inOrder()
        assertThat(state.coverage.first().done).containsExactly(GlucoseContext.FASTING, GlucoseContext.PRE_MEAL)
    }

    @Test
    fun tresibaReport_usesTheInsulinLog() = runTest {
        insulin.log(InsulinDose(type = InsulinType.TRESIBA, units = 42.0, administeredAt = now.minus(Duration.ofDays(1))))
        subscribe()
        assertThat(state.tresiba?.recommendation).isEqualTo(TresibaRecommendation.NeedMoreData(42.0, 0))
    }

    @Test
    fun applyCarbRatio_updatesTheProfile() = runTest {
        subscribe()
        viewModel.applyCarbRatio(11.0)
        assertThat(profile.profile.value.carbRatio).isEqualTo(11.0)
        assertThat(state.ratioApplied).isEqualTo(11.0)
    }
}
