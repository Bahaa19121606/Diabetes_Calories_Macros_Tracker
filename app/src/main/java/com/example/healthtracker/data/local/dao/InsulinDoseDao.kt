package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.healthtracker.data.local.entity.InsulinDoseEntity
import com.example.healthtracker.domain.model.InsulinType
import kotlinx.coroutines.flow.Flow

@Dao
interface InsulinDoseDao {

    @Insert
    suspend fun insert(dose: InsulinDoseEntity): Long

    @Update
    suspend fun update(dose: InsulinDoseEntity)

    @Delete
    suspend fun delete(dose: InsulinDoseEntity)

    @Query("SELECT * FROM insulin_doses WHERE id = :id")
    suspend fun getById(id: Long): InsulinDoseEntity?

    @Query("SELECT * FROM insulin_doses ORDER BY administered_at DESC")
    fun observeAll(): Flow<List<InsulinDoseEntity>>

    /** Half-open range [fromMillis, toMillis), newest first. */
    @Query(
        "SELECT * FROM insulin_doses WHERE administered_at >= :fromMillis AND administered_at < :toMillis " +
            "ORDER BY administered_at DESC"
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<InsulinDoseEntity>>

    /** E.g. "last Tresiba dose" to guard against a missed or doubled basal injection. */
    @Query("SELECT * FROM insulin_doses WHERE type = :type ORDER BY administered_at DESC LIMIT 1")
    fun observeLatestOfType(type: InsulinType): Flow<InsulinDoseEntity?>

    @Query(
        "SELECT COALESCE(SUM(units), 0.0) FROM insulin_doses " +
            "WHERE type = :type AND administered_at >= :fromMillis AND administered_at < :toMillis"
    )
    fun observeTotalUnitsBetween(type: InsulinType, fromMillis: Long, toMillis: Long): Flow<Double>
}
