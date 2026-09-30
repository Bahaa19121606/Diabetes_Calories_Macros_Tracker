package com.example.healthtracker.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.healthtracker.data.local.dao.CarbRatioTestDao
import com.example.healthtracker.data.local.dao.GlucoseReadingDao
import com.example.healthtracker.data.local.dao.InsulinDoseDao
import com.example.healthtracker.data.local.dao.MealDao
import com.example.healthtracker.data.local.dao.SleepSessionDao
import com.example.healthtracker.data.local.dao.WorkoutDao
import com.example.healthtracker.data.local.entity.CarbRatioTestEntity
import com.example.healthtracker.data.local.entity.GlucoseReadingEntity
import com.example.healthtracker.data.local.entity.InsulinDoseEntity
import com.example.healthtracker.data.local.entity.MealEntity
import com.example.healthtracker.data.local.entity.SleepSessionEntity
import com.example.healthtracker.data.local.entity.WorkoutEntity

@Database(
    entities = [
        MealEntity::class,
        InsulinDoseEntity::class,
        GlucoseReadingEntity::class,
        WorkoutEntity::class,
        CarbRatioTestEntity::class,
        SleepSessionEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2: dose-calculator columns on insulin_doses; workouts and carb_ratio_tests tables.
        AutoMigration(from = 1, to = 2),
        // v3: correction_factor on insulin_doses.
        AutoMigration(from = 2, to = 3),
        // v4: sleep_sessions table.
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun insulinDoseDao(): InsulinDoseDao
    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun carbRatioTestDao(): CarbRatioTestDao
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        const val NAME = "health_tracker.db"
    }
}
