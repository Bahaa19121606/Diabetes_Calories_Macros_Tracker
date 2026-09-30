package com.example.healthtracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.LabHbA1c
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val glucoseUnit: Flow<GlucoseUnit> = dataStore.data
        .map { prefs ->
            prefs[KEY_GLUCOSE_UNIT]
                ?.let { name -> GlucoseUnit.entries.firstOrNull { it.name == name } }
                ?: SettingsRepository.DEFAULT_GLUCOSE_UNIT
        }
        .distinctUntilChanged()

    override suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        dataStore.edit { it[KEY_GLUCOSE_UNIT] = unit.name }
    }

    override val dailyGoals: Flow<Macros> = dataStore.data
        .map { prefs ->
            val default = SettingsRepository.DEFAULT_DAILY_GOALS
            Macros(
                calories = prefs[KEY_GOAL_CALORIES] ?: default.calories,
                proteinGrams = prefs[KEY_GOAL_PROTEIN] ?: default.proteinGrams,
                carbsGrams = prefs[KEY_GOAL_CARBS] ?: default.carbsGrams,
                fatGrams = prefs[KEY_GOAL_FAT] ?: default.fatGrams,
            )
        }
        .distinctUntilChanged()

    override suspend fun setDailyGoals(goals: Macros) {
        dataStore.edit {
            it[KEY_GOAL_CALORIES] = goals.calories
            it[KEY_GOAL_PROTEIN] = goals.proteinGrams
            it[KEY_GOAL_CARBS] = goals.carbsGrams
            it[KEY_GOAL_FAT] = goals.fatGrams
        }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs ->
            prefs[KEY_THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: SettingsRepository.DEFAULT_THEME_MODE
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override val labHbA1c: Flow<LabHbA1c?> = dataStore.data
        .map { prefs ->
            val percent = prefs[KEY_HBA1C_PERCENT] ?: return@map null
            val day = prefs[KEY_HBA1C_DATE] ?: return@map null
            runCatching { LabHbA1c(percent, LocalDate.ofEpochDay(day)) }.getOrNull()
        }
        .distinctUntilChanged()

    override suspend fun setLabHbA1c(result: LabHbA1c?) {
        dataStore.edit {
            if (result == null) {
                it.remove(KEY_HBA1C_PERCENT)
                it.remove(KEY_HBA1C_DATE)
            } else {
                it[KEY_HBA1C_PERCENT] = result.percent
                it[KEY_HBA1C_DATE] = result.measuredOn.toEpochDay()
            }
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_HBA1C_PERCENT = doublePreferencesKey("lab_hba1c_percent")
        val KEY_HBA1C_DATE = longPreferencesKey("lab_hba1c_date")
        val KEY_GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val KEY_GOAL_CALORIES = intPreferencesKey("goal_calories")
        val KEY_GOAL_PROTEIN = intPreferencesKey("goal_protein_g")
        val KEY_GOAL_CARBS = intPreferencesKey("goal_carbs_g")
        val KEY_GOAL_FAT = intPreferencesKey("goal_fat_g")
    }
}
