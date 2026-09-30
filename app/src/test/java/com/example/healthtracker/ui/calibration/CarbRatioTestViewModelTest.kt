package com.example.healthtracker.ui.calibration

import com.example.healthtracker.domain.calibration.TestOutcome
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.testutil.FakeCarbRatioTestRepository
import com.example.healthtracker.testutil.FakeCarbTestReminderScheduler
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.FakeSettingsRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class CarbRatioTestViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val clock = MutableClock(now)
    private val tests = FakeCarbRatioTestRepository()
    private val glucose = FakeGlucoseRepository()
    private val insulin = FakeInsulinRepository()
    private val meals = FakeMealRepository()
    private val workouts = FakeWorkoutRepository()
    private val profile = FakeTherapyProfileRepository(TherapyProfile(carbRatio = 12.0))
    private val reminders = FakeCarbTestReminderScheduler()

    private val viewModel by lazy {
        CarbRatioTestViewModel(tests, glucose, insulin, meals, workouts, profile, FakeSettingsRepository(), reminders, clock)
    }
    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private suspend fun stableGlucose() {
        glucose.log(GlucoseReading(mgDl = 110, context = GlucoseContext.OTHER, measuredAt = now.minus(Duration.ofMinutes(45))))
        glucose.log(GlucoseReading(mgDl = 120, context = GlucoseContext.OTHER, measuredAt = now.minus(Duration.ofMinutes(5))))
    }

    private fun TestScope.startTest() {
        viewModel.start()
        clock.advanceBy(Duration.ofHours(2))
    }

    private fun record(index: Int, value: String) {
        viewModel.onCheckpointValueChange(index, value)
        viewModel.recordCheckpoint(index)
    }

    @Test
    fun cannotStartWithoutConfirmedStableGlucose() = runTest {
        subscribe()
        assertThat(state.canStart).isFalse()
        viewModel.start()
        assertThat(tests.tests.value).isEmpty()
    }

    @Test
    fun start_logsMealDoseAndTest_andSchedulesReminders() = runTest {
        stableGlucose()
        subscribe()
        assertThat(state.canStart).isTrue()
        assertThat(state.testDose).isEqualTo(4.0)

        viewModel.start()

        val test = tests.tests.value.single()
        assertThat(test.startMgDl).isEqualTo(120)
        assertThat(test.ratioTested).isEqualTo(12.0)
        assertThat(test.doseUnits).isEqualTo(4.0)
        val dose = insulin.doses.value.single()
        assertThat(dose.type).isEqualTo(InsulinType.NOVORAPID)
        assertThat(dose.bolus?.carbsGrams).isEqualTo(50)
        assertThat(meals.meals.value.single().macros.carbsGrams).isEqualTo(50)
        assertThat(reminders.scheduled).isEqualTo(test.id to now)
        assertThat(state.activeTest).isNotNull()
    }

    @Test
    fun customRatio_changesTheDose() = runTest {
        stableGlucose()
        subscribe()
        viewModel.onRatioChange("7")
        assertThat(state.testDose).isEqualTo(7.0)
    }

    @Test
    fun threeCheckpoints_completeAndGradeTheTest() = runTest {
        stableGlucose()
        subscribe()
        startTest()
        record(0, "170")
        clock.advanceBy(Duration.ofHours(1))
        record(1, "150")
        clock.advanceBy(Duration.ofHours(1))
        record(2, "135")

        val test = tests.tests.value.single()
        assertThat(test.status).isEqualTo(CarbRatioTestStatus.COMPLETED)
        assertThat(listOf(test.mgDlAt2h, test.mgDlAt3h, test.mgDlAt4h)).containsExactly(170, 150, 135).inOrder()
        assertThat(state.finished?.grade?.outcome).isEqualTo(TestOutcome.GOOD)
        assertThat(reminders.cancelCount).isEqualTo(1)
        assertThat(glucose.readings.value.count { it.note?.startsWith("Carb ratio test") == true }).isEqualTo(3)
    }

    @Test
    fun lowReading_endsTheTestEarly() = runTest {
        stableGlucose()
        subscribe()
        startTest()
        record(0, "64")

        assertThat(tests.tests.value.single().status).isEqualTo(CarbRatioTestStatus.COMPLETED)
        assertThat(state.finished?.grade?.outcome).isEqualTo(TestOutcome.LOW_DURING_TEST)
        assertThat(state.activeTest).isNull()
    }

    @Test
    fun applyingTheResult_updatesTheProfile() = runTest {
        stableGlucose()
        subscribe()
        startTest()
        record(0, "220")
        record(1, "210")
        record(2, "200") // +80 → too weak → 1:11

        viewModel.applyRatio(state.finished!!.grade)
        assertThat(profile.profile.value.carbRatio).isEqualTo(11.0)
    }

    @Test
    fun abort_cancelsReminders() = runTest {
        stableGlucose()
        subscribe()
        viewModel.start()
        viewModel.abort()
        assertThat(tests.tests.value.single().status).isEqualTo(CarbRatioTestStatus.ABORTED)
        assertThat(reminders.cancelCount).isEqualTo(1)
    }
}
