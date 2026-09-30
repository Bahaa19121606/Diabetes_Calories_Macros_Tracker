package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthtracker.domain.model.WorkoutIntensity

@Entity(tableName = "workouts", indices = [Index("started_at")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis (UTC). */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_min") val durationMinutes: Int,
    /** Stored by enum name via Room's built-in enum converter; never rename constants. */
    val intensity: WorkoutIntensity,
    val note: String? = null,
)
