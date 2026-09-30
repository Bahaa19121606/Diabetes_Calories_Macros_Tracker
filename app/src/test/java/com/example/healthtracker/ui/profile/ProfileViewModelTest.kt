package com.example.healthtracker.ui.profile

import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.FakeTherapyProfileRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.example.healthtracker.ui.common.InputError
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T20:00:00Z")
    private val profile = FakeTherapyProfileRepository()
    private val settings = FakeSettingsRepository()
    private val insulin = FakeInsulinRepository()

    private val viewModel by lazy { ProfileViewModel(profile, settings, insulin, MutableClock(now)) }
    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun defaults_areRatio12Factor50AndTarget140() = runTest {
        subscribe()
        assertThat(state.form.carbRatio).isEqualTo("12")
        assertThat(state.form.target).isEqualTo("140")
        assertThat(state.form.correctionFactor).isEqualTo("50")
        assertThat(state.isEditing).isFalse()
    }

    @Test
    fun save_persistsInMgDl_evenWhenEnteredInMmol() = runTest {
        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        subscribe()
        viewModel.onCorrectionFactorChange("1.4")
        viewModel.onTargetChange("7.8")
        viewModel.save()

        assertThat(profile.profile.value).isEqualTo(TherapyProfile(carbRatio = 12.0, correctionFactor = 25.0, targetMgDl = 141))
        assertThat(state.saved).isTrue()
        assertThat(state.isEditing).isFalse()
    }

    @Test
    fun save_rejectsImplausibleValues() = runTest {
        subscribe()
        viewModel.onCarbRatioChange("0")
        viewModel.onTargetChange("60")
        viewModel.save()

        assertThat(state.form.carbRatioError).isEqualTo(InputError.OUT_OF_RANGE)
        assertThat(state.form.targetError).isEqualTo(InputError.OUT_OF_RANGE)
        assertThat(profile.profile.value).isEqualTo(TherapyProfile())
    }

    @Test
    fun save_requiresACorrectionFactor() = runTest {
        subscribe()
        viewModel.onCorrectionFactorChange("")
        viewModel.save()

        assertThat(state.form.correctionFactorError).isEqualTo(InputError.EMPTY)
        assertThat(profile.profile.value).isEqualTo(TherapyProfile())
    }

    @Test
    fun formulaEstimates_comeFromTheInsulinLog() = runTest {
        (1L..3L).forEach { day ->
            val t = now.minus(Duration.ofDays(day))
            insulin.log(InsulinDose(type = InsulinType.TRESIBA, units = 42.0, administeredAt = t))
            insulin.log(InsulinDose(type = InsulinType.NOVORAPID, units = 30.0, administeredAt = t.plusSeconds(60)))
        }
        subscribe()

        assertThat(state.tresibaDose).isEqualTo(42.0)
        assertThat(state.dailyStats?.totalDailyDose).isEqualTo(72.0)
        assertThat(state.rule1800FactorMgDl).isEqualTo(25.0)
        assertThat(state.basalHeavy).isTrue() // 58%

        viewModel.useRule1800Factor()
        assertThat(state.form.correctionFactor).isEqualTo("25")
    }
}
