package com.example.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.healthtracker.data.local.entity.MealEntity
import com.example.healthtracker.data.local.model.MacroTotalsRow
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Update
    suspend fun update(meal: MealEntity)

    @Delete
    suspend fun delete(meal: MealEntity)

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getById(id: Long): MealEntity?

    @Query("SELECT * FROM meals ORDER BY eaten_at DESC")
    fun observeAll(): Flow<List<MealEntity>>

    /** Half-open range [fromMillis, toMillis), newest first. */
    @Query("SELECT * FROM meals WHERE eaten_at >= :fromMillis AND eaten_at < :toMillis ORDER BY eaten_at DESC")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<MealEntity>>

    /** Always emits a row; an empty range yields zeros. */
    @Query(
        """
        SELECT COALESCE(SUM(calories), 0) AS calories,
               COALESCE(SUM(protein_g), 0) AS protein_g,
               COALESCE(SUM(carbs_g), 0)   AS carbs_g,
               COALESCE(SUM(fat_g), 0)     AS fat_g
        FROM meals
        WHERE eaten_at >= :fromMillis AND eaten_at < :toMillis
        """
    )
    fun observeTotalsBetween(fromMillis: Long, toMillis: Long): Flow<MacroTotalsRow>
}
