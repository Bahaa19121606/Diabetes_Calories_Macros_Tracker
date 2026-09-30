package com.example.healthtracker.ui.bolus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.calibration.BolusCalculator
import com.example.healthtracker.domain.calibration.BolusInput
import com.example.healthtracker.domain.calibration.BolusResult
import com.example.healthtracker.domain.model.BolusBreakdown
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.CorrectionCheckPolicy
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.PostMealTimerPolicy
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.domain.timer.TimerTrigger
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.convertGlucoseInput
import com.example.healthtracker.ui.common.glucoseInputText
import com.example.healthtracker.ui.common.parseDecimal
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
import kotlin.math.roundToInt

data class BolusForm(
    val carbs: String = "",
    val carbsError: InputError? = null,
    val glucose: String = "",
    val glucoseError: InputError? = null,
    /** The logged reading the glucose field was prefilled from; null once the user types their own. */
    val glucoseSource: GlucoseReading? = null,
    /** Whole units added to (or taken off) the suggestion, e.g. for exercise coming up. */
    val adjustment: Int = 0,
)

sealed interface BolusMessage {
    data class Logged(val units: Double) : BolusMessage
    data class TimerScheduled(val fireAt: Instant, val exact: Boolean) : BolusMessage
    data class CorrectionCheckScheduled(val fireAt: Instant, val exact: Boolean) : BolusMessage
    data object SaveFailed : BolusMessage
}

data class BolusUiState(
    val form: BolusForm = BolusForm(),
    val meal: Meal? = null,
    val profile: TherapyProfile = TherapyProfile(),
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val result: BolusResult? = null,
    /** The parsed inputs behind [result], shown in the math breakdown. */
    val carbsUsed: Int? = null,
    val glucoseUsedMgDl: Int? = null,
    val isSaving: Boolean = false,
    /** Set after logging a meal dose: offer the post-meal timer, then leave the screen. */
    val timerPrompt: PostMealTimer? = null,
    /** Set after logging a correction-only dose: offer the 3-hour check, then leave the screen. */
    val correctionPrompt: CorrectionCheck? = null,
    val done: Boolean = false,
    val message: BolusMessage? = null,
) {
    val suggestedUnits: Double? get() = (result as? BolusResult.Suggestion)?.totalUnits

    /** The suggestion plus the user's adjustment; never negative. */
    val finalUnits: Double? get() = suggestedUnits?.let { (it + form.adjustment).coerceAtLeast(0.0) }
    val isAdjusted: Boolean get() = form.adjustment != 0
}

@HiltViewModel
class BolusCalculatorViewModel @Inject constructor(
    private val insulinRepository: InsulinRepository,
    private val glucoseRepository: GlucoseRepository,
    private val mealRepository: MealRepository,
    workoutRepository: WorkoutRepository,
    profileRepository: TherapyProfileRepository,
    settingsRepository: SettingsRepository,
    private val timerScheduler: PostMealTimerScheduler,
    private val correctionCheckScheduler: CorrectionCheckScheduler,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val local = MutableStateFlow(BolusUiState())
    private val mealId: Long? = savedStateHandle.get<Long>(ARG_MEAL_ID)?.takeIf { it > 0 }

    private val windowStart = clock.instant().minus(Duration.ofHours(12))
    private val windowEnd = clock.instant().plus(Duration.ofDays(1))
    private val doses = insulinRepository.observeBetween(windowStart, windowEnd)
    private val workouts = workoutRepository.observeBetween(windowStart, windowEnd)

    val uiState: StateFlow<BolusUiState> = combine(
        local,
        profileRepository.profile,
        settingsRepository.glucoseUnit,
        doses,
        workouts,
    ) { state, profile, unit, recentDoses, recentWorkouts ->
        val form = state.form.copy(
            carbsError = state.form.carbsError ?: fieldError(state.form.carbs, carbsOrNull(state.form)),
            glucoseError = state.form.glucoseError ?: fieldError(state.form.glucose, glucoseMgDlOrNull(state.form, unit)),
        )
        val withContext = state.copy(form = form, profile = profile, glucoseUnit = unit)
        val hasErrors = form.carbsError != null || form.glucoseError != null
        withContext.copy(
            result = if (hasErrors) null else calculate(withContext, recentDoses, recentWorkouts),
            carbsUsed = carbsOrNull(form),
            glucoseUsedMgDl = glucoseMgDlOrNull(form, unit),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BolusUiState())

    init {
        viewModelScope.launch {
            val unit = settingsRepository.glucoseUnit.first()
            val meal = mealId?.let { mealRepository.get(it) }
            // Prefill from the latest reading only if it's fresh enough to correct from.
            val latest = glucoseRepository.observeLatest().first()
                ?.takeIf { Duration.between(it.measuredAt, clock.instant()) <= BolusCalculator.GLUCOSE_MAX_AGE }
            local.update {
                it.copy(
                    meal = meal,
                    form = it.form.copy(
                        carbs = meal?.macros?.carbsGrams?.toString() ?: it.form.carbs,
                        glucose = latest?.let { r -> glucoseInputText(r.mgDl, unit) } ?: it.form.glucose,
                        glucoseSource = latest,
                    ),
                )
            }
        }
        viewModelScope.launch {
            var previous: GlucoseUnit? = null
            settingsRepository.glucoseUnit.collect { unit ->
                val from = previous
                previous = unit
                if (from != null && from != unit) {
                    local.update {
                        it.copy(form = it.form.copy(glucose = convertGlucoseInput(it.form.glucose, from, unit), glucoseError = null))
                    }
                }
            }
        }
    }

    fun onCarbsChange(text: String) = updateForm { it.copy(carbs = text, carbsError = null, adjustment = 0) }

    fun onGlucoseChange(text: String) =
        updateForm { it.copy(glucose = text, glucoseError = null, glucoseSource = null, adjustment = 0) }

    fun onAdjustmentChange(units: Int) = updateForm { it.copy(adjustment = units.coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)) }

    fun logDose() {
        val state = uiState.value
        if (state.isSaving) return
        val suggestion = state.result as? BolusResult.Suggestion ?: return
        val form = state.form
        val now = clock.instant()

        val units = state.finalUnits ?: return
        if (units <= 0.0) return
        val carbs = carbsOrNull(form) ?: 0
        // Only a dose taken exactly as calculated tells us something about the ratios.
        val breakdown = if (!state.isAdjusted && (carbs > 0 || suggestion.correctionUnits > 0)) {
            BolusBreakdown(carbs, state.profile.carbRatio, suggestion.correctionUnits, state.profile.correctionFactor)
        } else {
            null
        }
        val typedReading = glucoseMgDlOrNull(form, state.glucoseUnit)
            ?.takeIf { form.glucoseSource == null }
            ?.let { GlucoseReading(mgDl = it, context = if (carbs > 0) GlucoseContext.PRE_MEAL else GlucoseContext.OTHER, measuredAt = now) }

        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                typedReading?.let { glucoseRepository.log(it) }
                val dose = InsulinDose(
                    type = InsulinType.NOVORAPID,
                    units = units,
                    administeredAt = now,
                    mealId = state.meal?.id,
                    bolus = breakdown,
                )
                val id = insulinRepository.log(dose)
                // A correction is checked at 3 hours; a meal dose at 2.
                val correctionPrompt = if (carbs == 0 && breakdown != null) CorrectionCheckPolicy.checkFor(id, now, clock.instant()) else null
                val timerPrompt = if (carbs > 0) PostMealTimerPolicy.timerFor(now, TimerTrigger.NOVORAPID_DOSE, id, clock.instant()) else null
                local.update {
                    it.copy(
                        isSaving = false,
                        timerPrompt = timerPrompt,
                        correctionPrompt = correctionPrompt,
                        done = timerPrompt == null && correctionPrompt == null,
                        message = BolusMessage.Logged(units),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(isSaving = false, message = BolusMessage.SaveFailed) }
            }
        }
    }

    fun confirmTimer() {
        val prompt = local.value.timerPrompt ?: return
        val result = timerScheduler.schedule(prompt)
        local.update {
            it.copy(
                timerPrompt = null,
                done = true,
                message = BolusMessage.TimerScheduled(prompt.fireAt, exact = result == ScheduleResult.EXACT),
            )
        }
    }

    fun dismissTimerPrompt() = local.update { it.copy(timerPrompt = null, done = true) }

    fun confirmCorrectionCheck() {
        val prompt = local.value.correctionPrompt ?: return
        val result = correctionCheckScheduler.schedule(prompt)
        local.update {
            it.copy(
                correctionPrompt = null,
                done = true,
                message = BolusMessage.CorrectionCheckScheduled(prompt.fireAt, exact = result == ScheduleResult.EXACT),
            )
        }
    }

    fun dismissCorrectionPrompt() = local.update { it.copy(correctionPrompt = null, done = true) }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun calculate(state: BolusUiState, recentDoses: List<InsulinDose>, recentWorkouts: List<Workout>): BolusResult? {
        val form = state.form
        val carbs = carbsOrNull(form)
        val glucose = glucoseMgDlOrNull(form, state.glucoseUnit)
        if (carbs == null && glucose == null) return null
        return BolusCalculator.calculate(
            BolusInput(
                carbsGrams = carbs,
                glucoseMgDl = glucose,
                glucoseMeasuredAt = glucose?.let { form.glucoseSource?.measuredAt ?: clock.instant() },
                profile = state.profile,
                recentDoses = recentDoses,
                recentWorkouts = recentWorkouts,
                now = clock.instant(),
            ),
        )
    }

    /** A non-blank field that doesn't parse to a plausible value. */
    private fun fieldError(text: String, parsed: Int?): InputError? = when {
        text.isBlank() || parsed != null -> null
        text.trim().replace(',', '.').toDoubleOrNull() == null -> InputError.NOT_A_NUMBER
        else -> InputError.OUT_OF_RANGE
    }

    private fun carbsOrNull(form: BolusForm): Int? =
        (parseDecimal(form.carbs, 0.0..1_000.0) as? ParsedNumber.Valid)?.value?.roundToInt()

    private fun glucoseMgDlOrNull(form: BolusForm, unit: GlucoseUnit): Int? =
        (parseDecimal(form.glucose, 0.0..Double.MAX_VALUE) as? ParsedNumber.Valid)?.value
            ?.let(unit::toMgDl)
            ?.takeIf { it in GlucoseReading.MIN_MG_DL..GlucoseReading.MAX_MG_DL }

    private inline fun updateForm(transform: (BolusForm) -> BolusForm) = local.update { it.copy(form = transform(it.form)) }

    companion object {
        const val ARG_MEAL_ID = "mealId"
        const val MAX_ADJUSTMENT = 3
    }
}
