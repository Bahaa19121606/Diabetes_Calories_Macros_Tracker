package com.example.healthtracker.ui.bolus

import androidx.lifecycle.SavedStateHandle
import com.example.healthtracker.domain.calibration.BolusResult
import com.example.healthtracker.domain.model.BolusBreakdown
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.testutil.FakeCorrectionCheckScheduler
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.FakePostMealTimerScheduler
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.FakeTherapyProfileRepository
import com.example.healthtracker.testutil.FakeWorkoutRepository
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
class BolusCalculatorViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val clock = MutableClock(now)
    private val insulin = FakeInsulinRepository()
    private val glucose = FakeGlucoseRepository()
    private val meals = FakeMealRepository()
    private val workouts = FakeWorkoutRepository()
    private val profile = FakeTherapyProfileRepository(TherapyProfile(carbRatio = 12.0, correctionFactor = 25.0, targetMgDl = 140))
    private val settings = FakeSettingsRepository()
    private val scheduler = FakePostMealTimerScheduler()
    private val correctionChecks = FakeCorrectionCheckScheduler()
    private var savedState = SavedStateHandle()

    private val viewModel by lazy {
        BolusCalculatorViewModel(insulin, glucose, meals, workouts, profile, settings, scheduler, correctionChecks, clock, savedState)
    }
    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun prefillsFromMealAndFreshReading_andLogsTheBreakdown() = runTest {
        val mealId = meals.add(Meal(description = "rice", macros = Macros(500, 20, 60, 10), eatenAt = now))
        glucose.log(GlucoseReading(mgDl = 215, context = GlucoseContext.PRE_MEAL, measuredAt = now.minus(Duration.ofMinutes(5))))
        savedState = SavedStateHandle(mapOf(BolusCalculatorViewModel.ARG_MEAL_ID to mealId))
        subscribe()

        assertThat(state.form.carbs).isEqualTo("60")
        assertThat(state.form.glucose).isEqualTo("215")
        assertThat(state.suggestedUnits).isEqualTo(8.0) // 5 meal + 3 correction

        viewModel.logDose()

        val dose = insulin.doses.value.single()
        assertThat(dose.units).isEqualTo(8.0)
        assertThat(dose.mealId).isEqualTo(mealId)
        assertThat(dose.bolus).isEqualTo(BolusBreakdown(60, 12.0, 3.0, 25.0))
        assertThat(glucose.readings.value).hasSize(1) // prefilled reading isn't logged twice
        assertThat(state.timerPrompt?.fireAt).isEqualTo(now.plus(Duration.ofHours(2)))

        viewModel.confirmTimer()
        assertThat(scheduler.activeTimer.value).isNotNull()
        assertThat(state.done).isTrue()
    }

    @Test
    fun typedGlucose_isLoggedAsPreMealReading() = runTest {
        subscribe()
        viewModel.onCarbsChange("60")
        viewModel.onGlucoseChange("165")
        viewModel.logDose()

        val reading = glucose.readings.value.single()
        assertThat(reading.mgDl).isEqualTo(165)
        assertThat(reading.context).isEqualTo(GlucoseContext.PRE_MEAL)
        assertThat(insulin.doses.value.single().units).isEqualTo(6.0) // 5 + 1
    }

    @Test
    fun staleReading_isNotPrefilled() = runTest {
        glucose.log(GlucoseReading(mgDl = 215, context = GlucoseContext.OTHER, measuredAt = now.minus(Duration.ofMinutes(40))))
        subscribe()
        assertThat(state.form.glucose).isEmpty()
    }

    @Test
    fun adjustedDose_isLoggedWithoutBreakdown() = runTest {
        subscribe()
        viewModel.onCarbsChange("60")
        viewModel.onAdjustmentChange(-1)
        assertThat(state.finalUnits).isEqualTo(4.0)
        assertThat(state.isAdjusted).isTrue()
        viewModel.logDose()

        val dose = insulin.doses.value.single()
        assertThat(dose.units).isEqualTo(4.0)
        assertThat(dose.bolus).isNull()
    }

    @Test
    fun adjustment_isCappedAtThreeUnits_neverBelowZero_andResetByNewInputs() = runTest {
        subscribe()
        viewModel.onCarbsChange("24") // 2 u
        viewModel.onAdjustmentChange(9)
        assertThat(state.form.adjustment).isEqualTo(3)
        viewModel.onAdjustmentChange(-3)
        assertThat(state.finalUnits).isEqualTo(0.0)

        viewModel.onCarbsChange("36")
        assertThat(state.form.adjustment).isEqualTo(0)
        assertThat(state.finalUnits).isEqualTo(3.0)
    }

    @Test
    fun correctionOnly_offersTheThreeHourCheck_notThePostMealTimer() = runTest {
        subscribe()
        viewModel.onGlucoseChange("215") // (215 - 140) / 25 = 3 u
        viewModel.logDose()

        val dose = insulin.doses.value.single()
        assertThat(dose.bolus).isEqualTo(BolusBreakdown(0, 12.0, 3.0, 25.0))
        assertThat(glucose.readings.value.single().context).isEqualTo(GlucoseContext.OTHER)
        assertThat(state.timerPrompt).isNull()
        assertThat(state.correctionPrompt?.fireAt).isEqualTo(now.plus(Duration.ofHours(3)))

        viewModel.confirmCorrectionCheck()
        assertThat(correctionChecks.activeCheck.value?.doseId).isEqualTo(dose.id)
        assertThat(state.done).isTrue()
    }

    @Test
    fun invalidGlucose_showsErrorAndNoSuggestion() = runTest {
        subscribe()
        viewModel.onCarbsChange("60")
        viewModel.onGlucoseChange("5000")
        assertThat(state.form.glucoseError).isEqualTo(InputError.OUT_OF_RANGE)
        assertThat(state.result).isNull()
    }

    @Test
    fun mmolInput_isConverted() = runTest {
        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        subscribe()
        viewModel.onCarbsChange("60")
        viewModel.onGlucoseChange("11.9") // 214 mg/dL
        assertThat((state.result as BolusResult.Suggestion).correctionUnits).isWithin(0.01).of(2.96)
    }

    @Test
    fun low_blocksDosing() = runTest {
        subscribe()
        viewModel.onCarbsChange("60")
        viewModel.onGlucoseChange("62")
        assertThat(state.result).isEqualTo(BolusResult.TreatLow(62))

        viewModel.logDose()
        assertThat(insulin.doses.value).isEmpty()
    }
}
