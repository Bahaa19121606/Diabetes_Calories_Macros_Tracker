package com.example.healthtracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class SleepError { ZERO_LENGTH, TOO_LONG, WAKE_IN_FUTURE, SAVE_FAILED }

data class SleepUiState(
    val bedMinutes: Int = DEFAULT_BEDTIME,
    val wakeMinutes: Int = DEFAULT_WAKE,
    /** The morning the night ended. */
    val wakeDate: LocalDate,
    val today: LocalDate,
    val error: SleepError? = null,
    val isSaving: Boolean = false,
    /** Set once saved; the screen closes. */
    val saved: SleepSession? = null,
) {
    val durationMinutes: Int get() = SleepDialMath.durationMinutes(bedMinutes, wakeMinutes)

    companion object {
        const val DEFAULT_BEDTIME = 23 * 60
        const val DEFAULT_WAKE = 7 * 60
    }
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    fun onBedtimeChange(minutes: Int) = _uiState.update { it.copy(bedMinutes = minutes, error = null) }

    fun onWakeTimeChange(minutes: Int) = _uiState.update { it.copy(wakeMinutes = minutes, error = null) }

    fun onWakeDateChange(date: LocalDate) = _uiState.update { it.copy(wakeDate = date, error = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val (bedtime, wakeTime) = SleepDialMath.resolve(state.bedMinutes, state.wakeMinutes, state.wakeDate, clock.zone)
            ?: return _uiState.update { it.copy(error = SleepError.ZERO_LENGTH) }
        if (wakeTime.isAfter(clock.instant())) return _uiState.update { it.copy(error = SleepError.WAKE_IN_FUTURE) }
        if (state.durationMinutes > SleepSession.MAX_DURATION.toMinutes()) return _uiState.update { it.copy(error = SleepError.TOO_LONG) }

        val session = SleepSession(bedtime = bedtime, wakeTime = wakeTime)
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val id = sleepRepository.log(session)
                _uiState.update { it.copy(isSaving = false, saved = session.copy(id = id)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = SleepError.SAVE_FAILED) }
            }
        }
    }

    /** Before the usual wake time, the most recent full night ended yesterday. */
    private fun initialState(): SleepUiState {
        val today = LocalDate.now(clock)
        val beforeWake = LocalTime.now(clock).isBefore(LocalTime.of(SleepUiState.DEFAULT_WAKE / 60, 0))
        return SleepUiState(wakeDate = if (beforeWake) today.minusDays(1) else today, today = today)
    }
}
