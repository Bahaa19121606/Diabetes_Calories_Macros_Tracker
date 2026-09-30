package com.example.healthtracker.ui.t1d

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.timer.CorrectionCheckPolicy
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.PostMealTimerPolicy
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.domain.timer.TimerTrigger
import com.example.healthtracker.navigation.DeepLinks
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.TimeError
import com.example.healthtracker.ui.common.convertGlucoseInput
import com.example.healthtracker.ui.common.parseDecimal
import com.example.healthtracker.ui.common.resolveOccurredAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class T1dFormState(
    val insulinType: InsulinType = InsulinType.NOVORAPID,
    val insulinUnits: String = "",
    val insulinUnitsError: InputError? = null,
    /** null = now. */
    val insulinTime: Instant? = null,
    val insulinTimeError: TimeError? = null,
    val glucoseValue: String = "",
    val glucoseContext: GlucoseContext = GlucoseContext.PRE_MEAL,
    val glucoseError: InputError? = null,
    /** null = now. */
    val glucoseTime: Instant? = null,
    val glucoseTimeError: TimeError? = null,
    val isSaving: Boolean = false,
)

sealed interface T1dMessage {
    data class InsulinLogged(val type: InsulinType, val units: Double) : T1dMessage
    data class GlucoseLogged(val reading: GlucoseReading) : T1dMessage
    data class TimerScheduled(val fireAt: Instant) : T1dMessage
    /** Timer set, but may fire late; UI should offer to open the exact-alarm settings page. */
    data class TimerScheduledInexact(val fireAt: Instant) : T1dMessage
    data object TimerCancelled : T1dMessage
    data object SaveFailed : T1dMessage
}

data class T1dLogUiState(
    val form: T1dFormState = T1dFormState(),
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    /** Non-null → show "Start 2-hour post-meal timer?". */
    val timerPrompt: PostMealTimer? = null,
    val activeTimer: PostMealTimer? = null,
    val lastTresibaDose: InsulinDose? = null,
    val message: T1dMessage? = null,
)

@HiltViewModel
class T1dLogViewModel @Inject constructor(
    private val insulinRepository: InsulinRepository,
    private val glucoseRepository: GlucoseRepository,
    private val timerScheduler: PostMealTimerScheduler,
    private val correctionCheckScheduler: CorrectionCheckScheduler,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Everything owned by this ViewModel; the other fields of the UI state are merged in from their sources. */
    private val local = MutableStateFlow(T1dLogUiState())

    /** Null until the stored preference has loaded. */
    private val glucoseUnit: StateFlow<GlucoseUnit?> =
        settingsRepository.glucoseUnit.stateIn<GlucoseUnit?>(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<T1dLogUiState> = combine(
        local,
        glucoseUnit,
        timerScheduler.activeTimer,
        insulinRepository.observeLatest(InsulinType.TRESIBA),
    ) { state, unit, timer, lastTresiba ->
        state.copy(
            glucoseUnit = unit ?: SettingsRepository.DEFAULT_GLUCOSE_UNIT,
            activeTimer = timer,
            lastTresibaDose = lastTresiba,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), T1dLogUiState())

    init {
        // Opened from the post-meal notification deep link.
        savedStateHandle.get<String>(DeepLinks.ARG_CONTEXT)
            ?.let { name -> GlucoseContext.entries.firstOrNull { it.name == name } }
            ?.let(::prefillGlucoseContext)

        // Switching units anywhere in the app re-expresses a half-typed reading instead of
        // silently reinterpreting it (e.g. "25" mmol/L must not become 25 mg/dL).
        viewModelScope.launch {
            var previous: GlucoseUnit? = null
            glucoseUnit.filterNotNull().collect { unit ->
                val from = previous
                previous = unit
                if (from != null && from != unit) {
                    updateForm { it.copy(glucoseValue = convertGlucoseInput(it.glucoseValue, from, unit), glucoseError = null) }
                }
            }
        }
    }

    // region Insulin

    fun onInsulinTypeChange(type: InsulinType) = updateForm { it.copy(insulinType = type) }

    fun onInsulinUnitsChange(text: String) = updateForm { it.copy(insulinUnits = text, insulinUnitsError = null) }

    fun onInsulinTimeChange(time: Instant?) = updateForm { it.copy(insulinTime = time, insulinTimeError = null) }

    fun logInsulin() {
        val form = local.value.form
        if (form.isSaving) return
        val units = when (val parsed = parseDecimal(form.insulinUnits, INSULIN_UNITS_RANGE)) {
            is ParsedNumber.Invalid -> return updateForm { it.copy(insulinUnitsError = parsed.error) }
            is ParsedNumber.Valid -> parsed.value
        }
        val administeredAt = resolveOccurredAt(form.insulinTime, clock.instant())
            ?: return updateForm { it.copy(insulinTimeError = TimeError.IN_FUTURE) }
        val dose = InsulinDose(type = form.insulinType, units = units, administeredAt = administeredAt)

        saveThen(save = { insulinRepository.log(dose) }) { id ->
            // Counted from the injection time, so a back-dated dose gets a shorter (or no) timer.
            val prompt = if (dose.type == InsulinType.NOVORAPID) {
                PostMealTimerPolicy.timerFor(dose.administeredAt, TimerTrigger.NOVORAPID_DOSE, id, clock.instant())
            } else {
                null
            }
            local.update {
                it.copy(
                    form = it.form.copy(insulinUnits = "", insulinTime = null, isSaving = false),
                    timerPrompt = prompt ?: it.timerPrompt,
                    message = T1dMessage.InsulinLogged(dose.type, dose.units),
                )
            }
        }
    }

    // endregion
    // region Glucose

    fun onGlucoseValueChange(text: String) = updateForm { it.copy(glucoseValue = text, glucoseError = null) }

    fun onGlucoseContextChange(context: GlucoseContext) = updateForm { it.copy(glucoseContext = context) }

    fun onGlucoseTimeChange(time: Instant?) = updateForm { it.copy(glucoseTime = time, glucoseTimeError = null) }

    /** Entry point for the post-meal notification deep link. */
    fun prefillGlucoseContext(context: GlucoseContext) = onGlucoseContextChange(context)

    fun logGlucose() {
        val form = local.value.form
        val unit = glucoseUnit.value ?: return
        if (form.isSaving) return
        val mgDl = when (val parsed = parseDecimal(form.glucoseValue, 0.0..Double.MAX_VALUE)) {
            is ParsedNumber.Invalid -> return updateForm { it.copy(glucoseError = parsed.error) }
            is ParsedNumber.Valid -> unit.toMgDl(parsed.value)
        }
        if (mgDl !in GlucoseReading.MIN_MG_DL..GlucoseReading.MAX_MG_DL) {
            return updateForm { it.copy(glucoseError = InputError.OUT_OF_RANGE) }
        }
        val measuredAt = resolveOccurredAt(form.glucoseTime, clock.instant())
            ?: return updateForm { it.copy(glucoseTimeError = TimeError.IN_FUTURE) }
        val reading = GlucoseReading(mgDl = mgDl, context = form.glucoseContext, measuredAt = measuredAt)

        saveThen(save = { glucoseRepository.log(reading) }) { id ->
            // This reading is the follow-up to a correction: its reminder is no longer needed.
            correctionCheckScheduler.activeCheck.value
                ?.takeIf { CorrectionCheckPolicy.isFollowUp(it, reading.measuredAt) }
                ?.let { correctionCheckScheduler.cancel() }
            val prompt = when (reading.context) {
                GlucoseContext.PRE_MEAL ->
                    PostMealTimerPolicy.timerFor(reading.measuredAt, TimerTrigger.PRE_MEAL_GLUCOSE, id, clock.instant())
                GlucoseContext.POST_MEAL -> {
                    // The check is done, so the pending reminder is no longer needed.
                    if (timerScheduler.activeTimer.value != null) timerScheduler.cancel()
                    null
                }
                else -> null
            }
            local.update {
                it.copy(
                    form = it.form.copy(
                        glucoseValue = "",
                        glucoseTime = null,
                        isSaving = false,
                        // A post-meal check is a one-off; default back for the next reading.
                        glucoseContext = if (reading.context == GlucoseContext.POST_MEAL) {
                            GlucoseContext.PRE_MEAL
                        } else {
                            it.form.glucoseContext
                        },
                    ),
                    timerPrompt = prompt ?: it.timerPrompt,
                    message = T1dMessage.GlucoseLogged(reading.copy(id = id)),
                )
            }
        }
    }

    // endregion
    // region Timer

    fun confirmTimer() {
        val prompt = local.value.timerPrompt ?: return
        // Re-validate: the dialog may have been left open for a long time.
        val timer = PostMealTimerPolicy.timerFor(
            eventAt = prompt.fireAt.minus(PostMealTimerPolicy.DURATION),
            trigger = prompt.trigger,
            sourceId = prompt.sourceId,
            now = clock.instant(),
        )
        if (timer == null) {
            local.update { it.copy(timerPrompt = null) }
            return
        }
        val message = when (timerScheduler.schedule(timer)) {
            ScheduleResult.EXACT -> T1dMessage.TimerScheduled(timer.fireAt)
            ScheduleResult.INEXACT_PERMISSION_MISSING -> T1dMessage.TimerScheduledInexact(timer.fireAt)
        }
        local.update { it.copy(timerPrompt = null, message = message) }
    }

    fun dismissTimerPrompt() = local.update { it.copy(timerPrompt = null) }

    fun cancelActiveTimer() {
        timerScheduler.cancel()
        local.update { it.copy(message = T1dMessage.TimerCancelled) }
    }

    // endregion

    fun messageShown() = local.update { it.copy(message = null) }

    private inline fun updateForm(transform: (T1dFormState) -> T1dFormState) =
        local.update { it.copy(form = transform(it.form)) }

    private fun saveThen(save: suspend () -> Long, onSaved: (id: Long) -> Unit) {
        updateForm { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = try {
                save()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(form = it.form.copy(isSaving = false), message = T1dMessage.SaveFailed) }
                return@launch
            }
            onSaved(id)
        }
    }

    companion object {
        /** Upper bound is a typo guard (e.g. "55" for "5.5"), not a dosing limit. */
        val INSULIN_UNITS_RANGE = 0.05..100.0
    }
}
