package com.example.healthtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-wide appearance settings: the glucose unit and the theme. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val glucoseUnit: StateFlow<GlucoseUnit> = settingsRepository.glucoseUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_GLUCOSE_UNIT)

    /** Null until loaded, so the app doesn't flash the wrong theme on start. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setGlucoseUnit(unit: GlucoseUnit) {
        viewModelScope.launch { settingsRepository.setGlucoseUnit(unit) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}
