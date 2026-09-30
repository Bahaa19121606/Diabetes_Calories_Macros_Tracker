package com.example.healthtracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.healthtracker.data.settings.DataStoreSettingsRepository
import com.example.healthtracker.data.settings.DataStoreTherapyProfileRepository
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindTherapyProfileRepository(impl: DataStoreTherapyProfileRepository): TherapyProfileRepository

    companion object {
        // DataStore must be a process-wide singleton per file.
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    }
}
