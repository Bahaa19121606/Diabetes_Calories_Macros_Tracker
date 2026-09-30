package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Schema v4. */
@Entity(tableName = "sleep_sessions", indices = [Index("wake_time")])
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis (UTC). */
    val bedtime: Long,
    @ColumnInfo(name = "wake_time") val wakeTime: Long,
)
