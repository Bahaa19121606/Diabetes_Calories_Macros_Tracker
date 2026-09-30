package com.example.healthtracker.di

import com.example.healthtracker.data.repository.OfflineCarbRatioTestRepository
import com.example.healthtracker.data.repository.OfflineGlucoseRepository
import com.example.healthtracker.data.repository.OfflineInsulinRepository
import com.example.healthtracker.data.repository.OfflineMealRepository
import com.example.healthtracker.data.repository.OfflineSleepRepository
import com.example.healthtracker.data.repository.OfflineWorkoutRepository
import com.example.healthtracker.domain.repository.CarbRatioTestRepository
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.SleepRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindMealRepository(impl: OfflineMealRepository): MealRepository
    @Binds @Singleton abstract fun bindInsulinRepository(impl: OfflineInsulinRepository): InsulinRepository
    @Binds @Singleton abstract fun bindGlucoseRepository(impl: OfflineGlucoseRepository): GlucoseRepository
    @Binds @Singleton abstract fun bindWorkoutRepository(impl: OfflineWorkoutRepository): WorkoutRepository
    @Binds @Singleton abstract fun bindSleepRepository(impl: OfflineSleepRepository): SleepRepository
    @Binds @Singleton abstract fun bindCarbRatioTestRepository(impl: OfflineCarbRatioTestRepository): CarbRatioTestRepository
}
