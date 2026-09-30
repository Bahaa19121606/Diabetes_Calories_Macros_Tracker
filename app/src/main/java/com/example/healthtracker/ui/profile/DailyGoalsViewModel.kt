package com.example.healthtracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

enum class Goal(val label: String, val suffix: String, val range: ClosedFloatingPointRange<Double>) {
    CALORIES("Calories", "kcal", 500.0..10_000.0),
    CARBS("Carbs", "g", 20.0..1_000.0),
    PROTEIN("Protein", "g", 10.0..500.0),
    FAT("Fat", "g", 10.0..500.0),
}

data class GoalsUiState(
    val values: Map<Goal, String> = emptyMap(),
    val errors: Map<Goal, InputError> = emptyMap(),
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

/** Daily targets for the dashboard's progress rings. */
@HiltViewModel
class DailyGoalsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Non-null once the user starts typing; otherwise the form mirrors the saved goals. */
    private val editing = MutableStateFlow<GoalsUiState?>(null)
    private val saved = MutableStateFlow(false)

    val uiState: StateFlow<GoalsUiState> = combine(settingsRepository.dailyGoals, editing, saved) { goals, edit, isSaved ->
        (edit ?: GoalsUiState(values = goals.toValues())).copy(saved = isSaved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun onChange(goal: Goal, text: String) {
        saved.value = false
        editing.update { current ->
            val base = current ?: uiState.value
            base.copy(values = base.values + (goal to text), errors = base.errors - goal, isEditing = true)
        }
    }

    fun save() {
        val form = editing.value ?: return
        val parsed = Goal.entries.associateWith { parseDecimal(form.values[it].orEmpty(), it.range) }
        val errors = parsed.mapNotNull { (goal, result) -> (result as? ParsedNumber.Invalid)?.let { goal to it.error } }.toMap()
        if (errors.isNotEmpty()) {
            editing.value = form.copy(errors = errors)
            return
        }
        fun value(goal: Goal) = (parsed.getValue(goal) as ParsedNumber.Valid).value.roundToInt()
        viewModelScope.launch {
            settingsRepository.setDailyGoals(Macros(value(Goal.CALORIES), value(Goal.PROTEIN), value(Goal.CARBS), value(Goal.FAT)))
            editing.value = null
            saved.value = true
        }
    }

    fun savedShown() {
        saved.value = false
    }

    private fun Macros.toValues() = mapOf(
        Goal.CALORIES to calories.toString(),
        Goal.CARBS to carbsGrams.toString(),
        Goal.PROTEIN to proteinGrams.toString(),
        Goal.FAT to fatGrams.toString(),
    )
}
