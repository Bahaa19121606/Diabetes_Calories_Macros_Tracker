package com.example.healthtracker.di

import android.content.Context
import androidx.room.Room
import com.example.healthtracker.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No fallbackToDestructiveMigration(): this is medical history, schema changes
    // must ship explicit migrations (schemas are exported to app/schemas).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun provideMealDao(db: AppDatabase) = db.mealDao()
    @Provides fun provideInsulinDoseDao(db: AppDatabase) = db.insulinDoseDao()
    @Provides fun provideGlucoseReadingDao(db: AppDatabase) = db.glucoseReadingDao()
    @Provides fun provideWorkoutDao(db: AppDatabase) = db.workoutDao()
    @Provides fun provideCarbRatioTestDao(db: AppDatabase) = db.carbRatioTestDao()
    @Provides fun provideSleepSessionDao(db: AppDatabase) = db.sleepSessionDao()
}
