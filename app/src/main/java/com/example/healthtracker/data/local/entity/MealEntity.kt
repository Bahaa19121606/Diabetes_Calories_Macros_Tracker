package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meals",
    indices = [Index("eaten_at")],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val calories: Int,
    @ColumnInfo(name = "protein_g") val proteinGrams: Int,
    @ColumnInfo(name = "carbs_g") val carbsGrams: Int,
    @ColumnInfo(name = "fat_g") val fatGrams: Int,
    /** Epoch millis (UTC). */
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
)
