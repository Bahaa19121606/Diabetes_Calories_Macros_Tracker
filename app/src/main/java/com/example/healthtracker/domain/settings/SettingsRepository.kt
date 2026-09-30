package com.example.healthtracker.domain.settings

import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.LabHbA1c
import com.example.healthtracker.domain.model.Macros
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Display/input unit for glucose across the app. Storage is always mg/dL. */
    val glucoseUnit: Flow<GlucoseUnit>
    suspend fun setGlucoseUnit(unit: GlucoseUnit)

    /** Daily targets behind the dashboard's progress rings. */
    val dailyGoals: Flow<Macros>
    suspend fun setDailyGoals(goals: Macros)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** The latest lab HbA1c the user entered; the estimate is anchored to it. */
    val labHbA1c: Flow<LabHbA1c?>
    suspend fun setLabHbA1c(result: LabHbA1c?)

    companion object {
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        val DEFAULT_GLUCOSE_UNIT = GlucoseUnit.MG_DL
        val DEFAULT_DAILY_GOALS = Macros(calories = 2000, proteinGrams = 100, carbsGrams = 200, fatGrams = 70)
    }
}
