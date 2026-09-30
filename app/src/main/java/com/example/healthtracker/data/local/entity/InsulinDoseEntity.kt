package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthtracker.domain.model.InsulinType

@Entity(
    tableName = "insulin_doses",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            // Deleting a meal must never delete a dose record.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("administered_at"), Index("meal_id"), Index("type", "administered_at")],
)
data class InsulinDoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored by enum name via Room's built-in enum converter; never rename constants. */
    val type: InsulinType,
    val units: Double,
    /** Epoch millis (UTC). */
    @ColumnInfo(name = "administered_at") val administeredAt: Long,
    @ColumnInfo(name = "meal_id") val mealId: Long? = null,
    val note: String? = null,
    // Dose-calculator breakdown (schema v2); all null for manually entered doses.
    @ColumnInfo(name = "carbs_g") val carbsGrams: Int? = null,
    @ColumnInfo(name = "carb_ratio") val carbRatio: Double? = null,
    @ColumnInfo(name = "correction_units") val correctionUnits: Double? = null,
    // Schema v3.
    @ColumnInfo(name = "correction_factor") val correctionFactor: Double? = null,
)
