package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthtracker.domain.model.GlucoseContext

@Entity(
    tableName = "glucose_readings",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("measured_at"), Index("meal_id")],
)
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "mg_dl") val mgDl: Int,
    /** Stored by enum name via Room's built-in enum converter; never rename constants. */
    val context: GlucoseContext,
    /** Epoch millis (UTC). */
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "meal_id") val mealId: Long? = null,
    val note: String? = null,
)
