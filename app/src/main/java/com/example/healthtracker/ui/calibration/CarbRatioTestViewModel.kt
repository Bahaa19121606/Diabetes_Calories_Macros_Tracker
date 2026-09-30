package com.example.healthtracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.calibration.CarbRatioTestProtocol
import com.example.healthtracker.domain.calibration.GlucoseLimits
import com.example.healthtracker.domain.calibration.Precondition
import com.example.healthtracker.domain.calibration.PreconditionStatus
import com.example.healthtracker.domain.calibration.TestGrade
import com.example.healthtracker.domain.calibration.TestOutcome
import com.example.healthtracker.domain.model.BolusBreakdown
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.repository.CarbRatioTestRepository
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.timer.CarbTestReminderScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.parseDecimal
import com.example.healthtracker.ui.profile.formatRatio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class CarbTestForm(
    /** Ratio to test; blank = the current profile ratio. */
    val ratio: String = "",
    val ratioError: InputError? = null,
    /** Checkpoint index → typed reading (display unit). */
    val checkpointValues: Map<Int, String> = emptyMap(),
    val checkpointErrors: Map<Int, InputError> = emptyMap(),
)

sealed interface CarbTestMessage {
    data class Started(val exactReminders: Boolean) : CarbTestMessage
    data object Aborted : CarbTestMessage
    data class RatioApplied(val ratio: Double) : CarbTestMessage
    data object SaveFailed : CarbTestMessage
}

data class CarbTestUiState(
    val isLoading: Boolean = true,
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val profile: TherapyProfile = TherapyProfile(),
    val form: CarbTestForm = CarbTestForm(),
    val preconditions: List<Precondition> = emptyList(),
    val startMgDl: Int? = null,
    val activeTest: CarbRatioTest? = null,
    /** The test just finished on this screen, with its grade. */
    val finished: GradedTest? = null,
    val isSaving: Boolean = false,
    val message: CarbTestMessage? = null,
) {
    val ratioToTest: Double?
        get() = if (form.ratio.isBlank()) profile.carbRatio
        else (parseDecimal(form.ratio, TherapyProfile.CARB_RATIO_RANGE) as? ParsedNumber.Valid)?.value

    val testDose: Double? get() = ratioToTest?.let(CarbRatioTestProtocol::testDose)

    val canStart: Boolean
        get() = activeTest == null && !isSaving && testDose != null && startMgDl != null &&
            preconditions.isNotEmpty() && preconditions.all { it.status == PreconditionStatus.OK }
}

@HiltViewModel
class CarbRatioTestViewModel @Inject constructor(
    private val testRepository: CarbRatioTestRepository,
    private val glucoseRepository: GlucoseRepository,
    private val insulinRepository: InsulinRepository,
    private val mealRepository: MealRepository,
    workoutRepository: WorkoutRepository,
    private val profileRepository: TherapyProfileRepository,
    settingsRepository: SettingsRepository,
    private val reminders: CarbTestReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(CarbTestUiState())

    private val from = clock.instant().minus(Duration.ofDays(1))
    private val to = clock.instant().plus(Duration.ofDays(1))

    private val inputs = combine(
        glucoseRepository.observeBetween(from, to),
        insulinRepository.observeBetween(from, to),
        workoutRepository.observeBetween(from, to),
    ) { readings, doses, workouts ->
        val now = clock.instant()
        val preconditions = CarbRatioTestProtocol.checkPreconditions(readings, doses, workouts, now, clock.zone)
        val fresh = readings.filter { !it.measuredAt.isAfter(now) }.maxByOrNull { it.measuredAt }
            ?.takeIf { Duration.between(it.measuredAt, now) <= Duration.ofMinutes(15) }
        preconditions to fresh?.mgDl
    }

    val uiState: StateFlow<CarbTestUiState> = combine(
        local,
        inputs,
        testRepository.observeActive(),
        profileRepository.profile,
        settingsRepository.glucoseUnit,
    ) { state, (preconditions, startMgDl), active, profile, unit ->
        state.copy(
            isLoading = false,
            preconditions = preconditions,
            startMgDl = startMgDl,
            activeTest = active,
            profile = profile,
            glucoseUnit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarbTestUiState())

    fun onRatioChange(text: String) = updateForm { it.copy(ratio = text, ratioError = null) }

    fun onCheckpointValueChange(index: Int, text: String) =
        updateForm { it.copy(checkpointValues = it.checkpointValues + (index to text), checkpointErrors = it.checkpointErrors - index) }

    /** When a checkpoint's reading can be entered (15 minutes early at most). */
    fun checkpointOpensAt(test: CarbRatioTest, index: Int): Instant =
        test.startedAt.plus(CarbRatioTestProtocol.CHECKPOINTS[index]).minus(Duration.ofMinutes(15))

    fun start() {
        val state = uiState.value
        if (!state.canStart) {
            if (state.ratioToTest == null) updateForm { it.copy(ratioError = InputError.OUT_OF_RANGE) }
            return
        }
        val ratio = state.ratioToTest!!
        val dose = state.testDose!!
        val now = clock.instant()
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val meal = Meal(
                    description = "Carb ratio test: ${CarbRatioTestProtocol.TEST_CARBS_GRAMS} g fast carbs",
                    macros = Macros(calories = CarbRatioTestProtocol.TEST_CARBS_GRAMS * 4, proteinGrams = 0,
                        carbsGrams = CarbRatioTestProtocol.TEST_CARBS_GRAMS, fatGrams = 0),
                    eatenAt = now,
                )
                val mealId = mealRepository.add(meal)
                insulinRepository.log(
                    InsulinDose(
                        type = InsulinType.NOVORAPID,
                        units = dose,
                        administeredAt = now,
                        mealId = mealId,
                        note = "Carb ratio test 1:${formatRatio(ratio)}",
                        bolus = BolusBreakdown(CarbRatioTestProtocol.TEST_CARBS_GRAMS, ratio, correctionUnits = 0.0),
                    ),
                )
                val test = CarbRatioTest(
                    startedAt = now,
                    startMgDl = state.startMgDl!!,
                    carbsGrams = CarbRatioTestProtocol.TEST_CARBS_GRAMS,
                    ratioTested = ratio,
                    doseUnits = dose,
                )
                val id = testRepository.start(test)
                val result = reminders.schedule(id, now)
                local.update {
                    it.copy(isSaving = false, finished = null, message = CarbTestMessage.Started(result == ScheduleResult.EXACT))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(isSaving = false, message = CarbTestMessage.SaveFailed) }
            }
        }
    }

    fun recordCheckpoint(index: Int) {
        val state = uiState.value
        val test = state.activeTest ?: return
        val text = state.form.checkpointValues[index].orEmpty()
        val mgDl = when (val parsed = parseDecimal(text, 0.0..Double.MAX_VALUE)) {
            is ParsedNumber.Invalid -> return updateForm { it.copy(checkpointErrors = it.checkpointErrors + (index to parsed.error)) }
            is ParsedNumber.Valid -> state.glucoseUnit.toMgDl(parsed.value)
        }
        if (mgDl !in GlucoseReading.MIN_MG_DL..GlucoseReading.MAX_MG_DL) {
            return updateForm { it.copy(checkpointErrors = it.checkpointErrors + (index to InputError.OUT_OF_RANGE)) }
        }
        val now = clock.instant()
        var updated = when (index) {
            0 -> test.copy(mgDlAt2h = mgDl)
            1 -> test.copy(mgDlAt3h = mgDl)
            else -> test.copy(mgDlAt4h = mgDl)
        }
        val grade = CarbRatioTestProtocol.grade(updated)
        val ends = grade.outcome != TestOutcome.INCOMPLETE // a low ends it early; the 4-hour reading ends it normally
        if (ends) updated = updated.copy(status = CarbRatioTestStatus.COMPLETED, endedAt = now)

        viewModelScope.launch {
            try {
                glucoseRepository.log(
                    GlucoseReading(
                        mgDl = mgDl,
                        context = GlucoseContext.OTHER,
                        measuredAt = now,
                        note = "Carb ratio test · ${CarbRatioTestProtocol.CHECKPOINTS[index].toHours()} h",
                    ),
                )
                testRepository.update(updated)
                if (ends) reminders.cancel()
                local.update {
                    it.copy(
                        form = it.form.copy(checkpointValues = it.form.checkpointValues - index),
                        finished = if (ends) GradedTest(updated, grade) else it.finished,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(message = CarbTestMessage.SaveFailed) }
            }
        }
    }

    fun abort() {
        val test = uiState.value.activeTest ?: return
        viewModelScope.launch {
            testRepository.update(test.copy(status = CarbRatioTestStatus.ABORTED, endedAt = clock.instant()))
            reminders.cancel()
            local.update { it.copy(message = CarbTestMessage.Aborted) }
        }
    }

    fun applyRatio(grade: TestGrade) {
        val ratio = grade.suggestedRatio ?: return
        viewModelScope.launch {
            profileRepository.update(profileRepository.profile.first().copy(carbRatio = ratio))
            local.update { it.copy(finished = null, message = CarbTestMessage.RatioApplied(ratio)) }
        }
    }

    fun dismissResult() = local.update { it.copy(finished = null) }

    fun messageShown() = local.update { it.copy(message = null) }

    private inline fun updateForm(transform: (CarbTestForm) -> CarbTestForm) = local.update { it.copy(form = transform(it.form)) }

    companion object {
        /** Readings below this during the test: treat the low; the test ends. */
        const val LOW_MG_DL = GlucoseLimits.LOW_MG_DL
    }
}
