package com.example.healthtracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** Injected everywhere "now" matters, so tests can pin time and time zone. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
