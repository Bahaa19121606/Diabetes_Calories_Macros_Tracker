package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.healthtracker.data.local.entity.CarbRatioTestEntity
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CarbRatioTestDao {

    @Insert
    suspend fun insert(test: CarbRatioTestEntity): Long

    @Update
    suspend fun update(test: CarbRatioTestEntity)

    @Query("SELECT * FROM carb_ratio_tests WHERE id = :id")
    suspend fun getById(id: Long): CarbRatioTestEntity?

    @Query("SELECT * FROM carb_ratio_tests WHERE status = :status ORDER BY started_at DESC LIMIT 1")
    fun observeLatestWithStatus(status: CarbRatioTestStatus): Flow<CarbRatioTestEntity?>

    @Query("SELECT * FROM carb_ratio_tests ORDER BY started_at DESC")
    fun observeAll(): Flow<List<CarbRatioTestEntity>>
}
