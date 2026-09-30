package com.example.healthtracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Shares the app's settings DataStore file; keys are prefixed to avoid clashes. */
class DataStoreTherapyProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TherapyProfileRepository {

    override val profile: Flow<TherapyProfile> = dataStore.data
        .map { prefs ->
            TherapyProfile(
                carbRatio = prefs[KEY_CARB_RATIO] ?: TherapyProfile.DEFAULT_CARB_RATIO,
                correctionFactor = prefs[KEY_CORRECTION_FACTOR] ?: TherapyProfile.DEFAULT_CORRECTION_FACTOR,
                targetMgDl = prefs[KEY_TARGET] ?: TherapyProfile.DEFAULT_TARGET_MG_DL,
            )
        }
        .distinctUntilChanged()

    override suspend fun update(profile: TherapyProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_CARB_RATIO] = profile.carbRatio
            prefs[KEY_CORRECTION_FACTOR] = profile.correctionFactor
            prefs[KEY_TARGET] = profile.targetMgDl
        }
    }

    private companion object {
        val KEY_CARB_RATIO = doublePreferencesKey("therapy_carb_ratio")
        val KEY_CORRECTION_FACTOR = doublePreferencesKey("therapy_correction_factor")
        val KEY_TARGET = intPreferencesKey("therapy_target_mg_dl")
    }
}
