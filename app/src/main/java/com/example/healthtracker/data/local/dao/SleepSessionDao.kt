package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.healthtracker.data.local.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {

    @Insert
    suspend fun insert(session: SleepSessionEntity): Long

    @Delete
    suspend fun delete(session: SleepSessionEntity)

    /** Sessions overlapping [fromMillis, toMillis), newest first. */
    @Query("SELECT * FROM sleep_sessions WHERE wake_time > :fromMillis AND bedtime < :toMillis ORDER BY wake_time DESC")
    fun observeOverlapping(fromMillis: Long, toMillis: Long): Flow<List<SleepSessionEntity>>
}
