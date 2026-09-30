package com.example.healthtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.model.WorkoutIntensity
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.TimeError
import com.example.healthtracker.ui.common.parseDecimal
import com.example.healthtracker.ui.common.resolveOccurredAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface WorkoutMessage {
    data class Logged(val workout: Workout) : WorkoutMessage
    data object SaveFailed : WorkoutMessage
}

data class WorkoutUiState(
    val intensity: WorkoutIntensity = WorkoutIntensity.HEAVY_LIFTING,
    val duration: String = "60",
    val durationError: InputError? = null,
    /** Start time; null = now. */
    val startedAt: Instant? = null,
    val timeError: TimeError? = null,
    val isSaving: Boolean = false,
    val message: WorkoutMessage? = null,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    fun onIntensityChange(intensity: WorkoutIntensity) = _uiState.update { it.copy(intensity = intensity) }

    fun onDurationChange(text: String) = _uiState.update { it.copy(duration = text, durationError = null) }

    fun onStartedAtChange(time: Instant?) = _uiState.update { it.copy(startedAt = time, timeError = null) }

    fun log() {
        val state = _uiState.value
        if (state.isSaving) return
        val minutes = when (val parsed = parseDecimal(state.duration, 1.0..600.0)) {
            is ParsedNumber.Invalid -> return _uiState.update { it.copy(durationError = parsed.error) }
            is ParsedNumber.Valid -> parsed.value.roundToInt()
        }
        val startedAt = resolveOccurredAt(state.startedAt, clock.instant())
            ?: return _uiState.update { it.copy(timeError = TimeError.IN_FUTURE) }
        val workout = Workout(startedAt = startedAt, durationMinutes = minutes, intensity = state.intensity)

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val id = workoutRepository.log(workout)
                _uiState.update { it.copy(isSaving = false, startedAt = null, message = WorkoutMessage.Logged(workout.copy(id = id))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = WorkoutMessage.SaveFailed) }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }
}
