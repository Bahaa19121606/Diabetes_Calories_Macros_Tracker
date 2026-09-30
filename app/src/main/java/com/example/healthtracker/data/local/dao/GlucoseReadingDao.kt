package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.healthtracker.data.local.entity.GlucoseReadingEntity
import com.example.healthtracker.domain.model.GlucoseContext
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseReadingDao {

    @Insert
    suspend fun insert(reading: GlucoseReadingEntity): Long

    @Update
    suspend fun update(reading: GlucoseReadingEntity)

    @Delete
    suspend fun delete(reading: GlucoseReadingEntity)

    @Query("SELECT * FROM glucose_readings WHERE id = :id")
    suspend fun getById(id: Long): GlucoseReadingEntity?

    @Query("SELECT * FROM glucose_readings ORDER BY measured_at DESC")
    fun observeAll(): Flow<List<GlucoseReadingEntity>>

    /** Half-open range [fromMillis, toMillis), newest first. */
    @Query(
        "SELECT * FROM glucose_readings WHERE measured_at >= :fromMillis AND measured_at < :toMillis " +
            "ORDER BY measured_at DESC"
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<GlucoseReadingEntity>>

    @Query("SELECT * FROM glucose_readings ORDER BY measured_at DESC LIMIT 1")
    fun observeLatest(): Flow<GlucoseReadingEntity?>

    @Query("SELECT * FROM glucose_readings WHERE context = :context ORDER BY measured_at DESC LIMIT 1")
    suspend fun getLatestWithContext(context: GlucoseContext): GlucoseReadingEntity?
}
