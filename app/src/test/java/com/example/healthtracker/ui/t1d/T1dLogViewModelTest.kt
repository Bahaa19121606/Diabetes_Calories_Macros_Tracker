package com.example.healthtracker.ui.t1d

import androidx.lifecycle.SavedStateHandle
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.TimerTrigger
import com.example.healthtracker.testutil.FakeCorrectionCheckScheduler
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakePostMealTimerScheduler
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.TimeError
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
class T1dLogViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val twoHoursLater = now.plus(Duration.ofHours(2))
    private val clock = MutableClock(now)
    private val insulin = FakeInsulinRepository()
    private val glucose = FakeGlucoseRepository()
    private val scheduler = FakePostMealTimerScheduler()
    private val correctionChecks = FakeCorrectionCheckScheduler()
    private val settings = FakeSettingsRepository()
    private var savedStateHandle = SavedStateHandle()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { T1dLogViewModel(insulin, glucose, scheduler, correctionChecks, settings, clock, savedStateHandle) }

    /** uiState is WhileSubscribed, so keep a collector alive like the UI would. */
    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private val state get() = viewModel.uiState.value

    // region Insulin

    @Test
    fun novorapid_isSavedAndPromptsTimer() = runTest {
        subscribe()
        viewModel.onInsulinUnitsChange("4,5")
        viewModel.logInsulin()

        val dose = insulin.doses.value.single()
        assertThat(dose.type).isEqualTo(InsulinType.NOVORAPID)
        assertThat(dose.units).isEqualTo(4.5)
        assertThat(dose.administeredAt).isEqualTo(now)
        assertThat(state.timerPrompt).isEqualTo(PostMealTimer(twoHoursLater, TimerTrigger.NOVORAPID_DOSE, dose.id))
        assertThat(state.form.insulinUnits).isEmpty()
        assertThat(state.message).isEqualTo(T1dMessage.InsulinLogged(InsulinType.NOVORAPID, 4.5))
    }

    @Test
    fun tresiba_doesNotPromptTimer_andUpdatesLastDose() = runTest {
        subscribe()
        viewModel.onInsulinTypeChange(InsulinType.TRESIBA)
        viewModel.onInsulinUnitsChange("20")
        viewModel.logInsulin()

        assertThat(state.timerPrompt).isNull()
        assertThat(state.lastTresibaDose?.units).isEqualTo(20.0)
    }

    @Test
    fun invalidUnits_showErrorAndSaveNothing() = runTest {
        subscribe()
        for ((input, error) in listOf("" to InputError.EMPTY, "4u" to InputError.NOT_A_NUMBER, "0" to InputError.OUT_OF_RANGE)) {
            viewModel.onInsulinUnitsChange(input)
            viewModel.logInsulin()
            assertThat(state.form.insulinUnitsError).isEqualTo(error)
        }
        assertThat(insulin.doses.value).isEmpty()

        viewModel.onInsulinUnitsChange("4")
        assertThat(state.form.insulinUnitsError).isNull()
    }

    @Test
    fun saveFailure_keepsInputAndReports() = runTest {
        subscribe()
        insulin.failWrites = true
        viewModel.onInsulinUnitsChange("4")
        viewModel.logInsulin()

        assertThat(state.message).isEqualTo(T1dMessage.SaveFailed)
        assertThat(state.form.insulinUnits).isEqualTo("4")
        assertThat(state.form.isSaving).isFalse()
        assertThat(state.timerPrompt).isNull()
    }

    // endregion
    // region Glucose

    @Test
    fun preMealGlucose_convertsMmolAndPromptsTimer() = runTest {
        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        subscribe()
        viewModel.onGlucoseValueChange("5.6")
        viewModel.logGlucose()

        val reading = glucose.readings.value.single()
        assertThat(reading.mgDl).isEqualTo(101)
        assertThat(reading.context).isEqualTo(GlucoseContext.PRE_MEAL)
        assertThat(state.timerPrompt).isEqualTo(PostMealTimer(twoHoursLater, TimerTrigger.PRE_MEAL_GLUCOSE, reading.id))
    }

    @Test
    fun mgDlIsTheDefault_andStoredAsIs() = runTest {
        subscribe()
        assertThat(state.glucoseUnit).isEqualTo(GlucoseUnit.MG_DL)
        viewModel.onGlucoseValueChange("142")
        viewModel.logGlucose()
        assertThat(glucose.readings.value.single().mgDl).isEqualTo(142)
    }

    @Test
    fun implausibleGlucose_forUnitIsRejected() = runTest {
        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        subscribe()
        viewModel.onGlucoseValueChange("120") // mg/dL typed while in mmol/L mode
        viewModel.logGlucose()
        assertThat(state.form.glucoseError).isEqualTo(InputError.OUT_OF_RANGE)
        assertThat(glucose.readings.value).isEmpty()
    }

    @Test
    fun postMealGlucose_cancelsActiveTimer_andResetsContext() = runTest {
        subscribe()
        viewModel.onInsulinUnitsChange("5")
        viewModel.logInsulin()
        viewModel.confirmTimer()
        assertThat(state.activeTimer).isNotNull()

        viewModel.prefillGlucoseContext(GlucoseContext.POST_MEAL)
        viewModel.onGlucoseValueChange("148")
        viewModel.logGlucose()

        assertThat(scheduler.cancelCount).isEqualTo(1)
        assertThat(state.activeTimer).isNull()
        assertThat(state.timerPrompt).isNull()
        assertThat(state.form.glucoseContext).isEqualTo(GlucoseContext.PRE_MEAL)
    }

    // endregion
    // region Timer

    @Test
    fun confirmTimer_schedulesExactAlarm() = runTest {
        subscribe()
        viewModel.onInsulinUnitsChange("5")
        viewModel.logInsulin()
        viewModel.confirmTimer()

        assertThat(state.timerPrompt).isNull()
        assertThat(state.activeTimer?.fireAt).isEqualTo(twoHoursLater)
        assertThat(state.message).isEqualTo(T1dMessage.TimerScheduled(twoHoursLater))
    }

    @Test
    fun confirmTimer_withoutExactPermission_reportsInexact() = runTest {
        subscribe()
        scheduler.exactAllowed = false
        viewModel.onInsulinUnitsChange("5")
        viewModel.logInsulin()
        viewModel.confirmTimer()

        assertThat(state.activeTimer).isNotNull()
        assertThat(state.message).isEqualTo(T1dMessage.TimerScheduledInexact(twoHoursLater))
    }

    @Test
    fun confirmTimer_afterPromptWentStale_schedulesNothing() = runTest {
        subscribe()
        viewModel.onInsulinUnitsChange("5")
        viewModel.logInsulin()
        clock.advanceBy(Duration.ofHours(2))

        viewModel.confirmTimer()

        assertThat(state.activeTimer).isNull()
        assertThat(state.timerPrompt).isNull()
    }

    @Test
    fun dismissAndCancel() = runTest {
        subscribe()
        viewModel.onInsulinUnitsChange("5")
        viewModel.logInsulin()
        viewModel.dismissTimerPrompt()
        assertThat(state.timerPrompt).isNull()
        assertThat(state.activeTimer).isNull()

        viewModel.onInsulinUnitsChange("2")
        viewModel.logInsulin()
        viewModel.confirmTimer()
        viewModel.cancelActiveTimer()
        assertThat(state.activeTimer).isNull()
        assertThat(state.message).isEqualTo(T1dMessage.TimerCancelled)
    }

    // endregion
    // region Units

    @Test
    fun switchingUnit_convertsTypedValue() = runTest {
        subscribe()
        viewModel.onGlucoseValueChange("101")

        settings.setGlucoseUnit(GlucoseUnit.MMOL_L)
        assertThat(state.glucoseUnit).isEqualTo(GlucoseUnit.MMOL_L)
        assertThat(state.form.glucoseValue).isEqualTo("5.6")

        settings.setGlucoseUnit(GlucoseUnit.MG_DL)
        assertThat(state.form.glucoseValue).isEqualTo("101")

        viewModel.logGlucose()
        assertThat(glucose.readings.value.single().mgDl).isEqualTo(101)
    }

    // endregion
    // region Back-dating

    @Test
    fun backdatedNovorapid_timerCountsFromInjection() = runTest {
        subscribe()
        val injectedAt = now.minus(Duration.ofMinutes(30))
        viewModel.onInsulinTimeChange(injectedAt)
        viewModel.onInsulinUnitsChange("6")
        viewModel.logInsulin()

        assertThat(insulin.doses.value.single().administeredAt).isEqualTo(injectedAt)
        assertThat(state.timerPrompt?.fireAt).isEqualTo(now.plus(Duration.ofMinutes(90)))
        assertThat(state.form.insulinTime).isNull() // reset to "now" for the next entry
    }

    @Test
    fun novorapidLoggedMoreThanTwoHoursAgo_noTimerPrompt() = runTest {
        subscribe()
        viewModel.onInsulinTimeChange(now.minus(Duration.ofHours(3)))
        viewModel.onInsulinUnitsChange("6")
        viewModel.logInsulin()

        assertThat(insulin.doses.value).hasSize(1)
        assertThat(state.timerPrompt).isNull()
    }

    @Test
    fun backdatedGlucose_isStoredAtChosenTime() = runTest {
        subscribe()
        val measuredAt = now.minus(Duration.ofHours(1))
        viewModel.onGlucoseTimeChange(measuredAt)
        viewModel.onGlucoseValueChange("110")
        viewModel.logGlucose()

        assertThat(glucose.readings.value.single().measuredAt).isEqualTo(measuredAt)
        assertThat(state.timerPrompt?.fireAt).isEqualTo(now.plus(Duration.ofHours(1)))
    }

    @Test
    fun futureTimes_areRejected() = runTest {
        subscribe()
        val later = now.plus(Duration.ofMinutes(10))
        viewModel.onInsulinTimeChange(later)
        viewModel.onInsulinUnitsChange("4")
        viewModel.logInsulin()
        viewModel.onGlucoseTimeChange(later)
        viewModel.onGlucoseValueChange("110")
        viewModel.logGlucose()

        assertThat(state.form.insulinTimeError).isEqualTo(TimeError.IN_FUTURE)
        assertThat(state.form.glucoseTimeError).isEqualTo(TimeError.IN_FUTURE)
        assertThat(insulin.doses.value).isEmpty()
        assertThat(glucose.readings.value).isEmpty()
    }

    // endregion
    // region Deep link

    @Test
    fun deepLinkArgument_prefillsPostMealContext() = runTest {
        savedStateHandle = SavedStateHandle(mapOf("context" to "POST_MEAL"))
        subscribe()
        assertThat(state.form.glucoseContext).isEqualTo(GlucoseContext.POST_MEAL)
    }

    // endregion

    @Test
    fun followUpReading_cancelsTheCorrectionCheck_anEarlierOneDoesNot() = runTest {
        correctionChecks.schedule(CorrectionCheck(doseId = 1, dosedAt = now.minus(Duration.ofHours(2))))
        subscribe()

        viewModel.onGlucoseContextChange(GlucoseContext.OTHER)
        viewModel.onGlucoseValueChange("150")
        viewModel.logGlucose() // 2 h after: too early to count
        assertThat(correctionChecks.activeCheck.value).isNotNull()

        clock.advanceBy(Duration.ofMinutes(40))
        viewModel.onGlucoseValueChange("140")
        viewModel.logGlucose() // 2 h 40 min after
        assertThat(correctionChecks.activeCheck.value).isNull()
    }
}
