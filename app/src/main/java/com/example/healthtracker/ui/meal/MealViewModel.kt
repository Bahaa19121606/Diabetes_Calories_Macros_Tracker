package com.example.healthtracker.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.analysis.MacroAnalyzer
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.TimeError
import com.example.healthtracker.ui.common.parseDecimal
import com.example.healthtracker.ui.common.resolveOccurredAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

enum class MacroField(val max: Double) {
    CALORIES(20_000.0),
    PROTEIN(2_000.0),
    CARBS(2_000.0),
    FAT(2_000.0),
}

/** Text-field values while the user edits the AI's estimate. */
data class MacroDraft(
    val values: Map<MacroField, String>,
    val errors: Map<MacroField, InputError> = emptyMap(),
) {
    fun withValue(field: MacroField, text: String) = copy(values = values + (field to text), errors = errors - field)

    sealed interface Validation {
        data class Valid(val macros: Macros) : Validation
        data class Invalid(val draft: MacroDraft) : Validation
    }

    fun validate(): Validation {
        val parsed = MacroField.entries.associateWith { parseDecimal(values[it].orEmpty(), 0.0..it.max) }
        val newErrors = parsed.mapNotNull { (field, result) -> (result as? ParsedNumber.Invalid)?.let { field to it.error } }.toMap()
        if (newErrors.isNotEmpty()) return Validation.Invalid(copy(errors = newErrors))
        val grams = parsed.mapValues { (_, result) -> (result as ParsedNumber.Valid).value.roundToInt() }
        return Validation.Valid(
            Macros(
                calories = grams.getValue(MacroField.CALORIES),
                proteinGrams = grams.getValue(MacroField.PROTEIN),
                carbsGrams = grams.getValue(MacroField.CARBS),
                fatGrams = grams.getValue(MacroField.FAT),
            ),
        )
    }

    companion object {
        fun from(macros: Macros) = MacroDraft(
            mapOf(
                MacroField.CALORIES to macros.calories.toString(),
                MacroField.PROTEIN to macros.proteinGrams.toString(),
                MacroField.CARBS to macros.carbsGrams.toString(),
                MacroField.FAT to macros.fatGrams.toString(),
            ),
        )
    }
}

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Error(val reason: AnalysisError) : AnalysisState

    /** AI result shown in the review overlay; [draft] is non-null while the user edits it. */
    data class Ready(val aiMacros: Macros, val draft: MacroDraft? = null, val notes: String? = null) : AnalysisState
}

enum class AnalysisError { NETWORK, MALFORMED_RESPONSE, NOT_CONFIGURED, RATE_LIMITED, SERVICE_BUSY }

sealed interface MealMessage {
    data class Saved(val mealId: Long, val macros: Macros, val edited: Boolean) : MealMessage
    data object SaveFailed : MealMessage
}

data class MealUiState(
    val input: String = "",
    /** When the meal was eaten; null = now. */
    val eatenAt: Instant? = null,
    val timeError: TimeError? = null,
    val analysis: AnalysisState = AnalysisState.Idle,
    val isSaving: Boolean = false,
    val message: MealMessage? = null,
) {
    val canAnalyze: Boolean
        get() = input.isNotBlank() && analysis !is AnalysisState.Loading && analysis !is AnalysisState.Ready && !isSaving
}

@HiltViewModel
class MealViewModel @Inject constructor(
    private val analyzer: MacroAnalyzer,
    private val mealRepository: MealRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun onInputChange(text: String) {
        // Any result (or in-flight request) belongs to the old text.
        analysisJob?.cancel()
        _uiState.update { it.copy(input = text, analysis = AnalysisState.Idle) }
    }

    fun onEatenAtChange(time: Instant?) = _uiState.update { it.copy(eatenAt = time, timeError = null) }

    fun analyze() {
        val state = _uiState.value
        if (!state.canAnalyze) return
        val description = state.input.trim()

        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(analysis = AnalysisState.Loading) }
            val analysis = when (val result = analyzer.analyze(description)) {
                is MacroAnalysisResult.Success -> AnalysisState.Ready(result.macros, notes = result.notes)
                is MacroAnalysisResult.NetworkError -> AnalysisState.Error(AnalysisError.NETWORK)
                is MacroAnalysisResult.MalformedResponse -> AnalysisState.Error(AnalysisError.MALFORMED_RESPONSE)
                is MacroAnalysisResult.NotConfigured -> AnalysisState.Error(AnalysisError.NOT_CONFIGURED)
                is MacroAnalysisResult.RateLimited -> AnalysisState.Error(AnalysisError.RATE_LIMITED)
                is MacroAnalysisResult.ServiceUnavailable -> AnalysisState.Error(AnalysisError.SERVICE_BUSY)
            }
            _uiState.update { it.copy(analysis = analysis) }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.update { it.copy(analysis = AnalysisState.Idle) }
    }

    // region Review overlay

    fun startEditing() = updateReady { it.copy(draft = MacroDraft.from(it.aiMacros)) }

    fun onMacroFieldChange(field: MacroField, text: String) =
        updateReady { ready -> ready.draft?.let { ready.copy(draft = it.withValue(field, text)) } ?: ready }

    /** Back from the editor to the AI's original numbers. */
    fun cancelEditing() = updateReady { it.copy(draft = null) }

    /** Closes the overlay without saving; the description is kept so it can be re-analysed. */
    fun dismissReview() = _uiState.update { it.copy(analysis = AnalysisState.Idle, timeError = null) }

    fun save() {
        val state = _uiState.value
        val ready = state.analysis as? AnalysisState.Ready ?: return
        if (state.isSaving) return

        val macros = when (val draft = ready.draft) {
            null -> ready.aiMacros
            else -> when (val validated = draft.validate()) {
                is MacroDraft.Validation.Valid -> validated.macros
                is MacroDraft.Validation.Invalid -> return updateReady { it.copy(draft = validated.draft) }
            }
        }
        val eatenAt = resolveOccurredAt(state.eatenAt, clock.instant())
            ?: return _uiState.update { it.copy(timeError = TimeError.IN_FUTURE) }
        val meal = Meal(description = state.input.trim(), macros = macros, eatenAt = eatenAt)

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val id = mealRepository.add(meal)
                _uiState.value = MealUiState(message = MealMessage.Saved(id, macros, edited = macros != ready.aiMacros))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = MealMessage.SaveFailed) }
            }
        }
    }

    // endregion

    fun discard() {
        analysisJob?.cancel()
        _uiState.value = MealUiState()
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private inline fun updateReady(transform: (AnalysisState.Ready) -> AnalysisState.Ready) =
        _uiState.update { state ->
            val ready = state.analysis as? AnalysisState.Ready ?: return@update state
            state.copy(analysis = transform(ready))
        }
}
