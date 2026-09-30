package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.healthtracker.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    /** Half-open range [fromMillis, toMillis), newest first. */
    @Query("SELECT * FROM workouts WHERE started_at >= :fromMillis AND started_at < :toMillis ORDER BY started_at DESC")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutEntity>>
}
