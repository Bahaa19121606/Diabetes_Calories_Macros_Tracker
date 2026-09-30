package com.example.healthtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthtracker.domain.model.CarbRatioTestStatus

@Entity(tableName = "carb_ratio_tests", indices = [Index("started_at")])
data class CarbRatioTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis (UTC). */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "start_mg_dl") val startMgDl: Int,
    @ColumnInfo(name = "carbs_g") val carbsGrams: Int,
    @ColumnInfo(name = "ratio_tested") val ratioTested: Double,
    @ColumnInfo(name = "dose_units") val doseUnits: Double,
    @ColumnInfo(name = "mg_dl_2h") val mgDlAt2h: Int? = null,
    @ColumnInfo(name = "mg_dl_3h") val mgDlAt3h: Int? = null,
    @ColumnInfo(name = "mg_dl_4h") val mgDlAt4h: Int? = null,
    /** Stored by enum name via Room's built-in enum converter; never rename constants. */
    val status: CarbRatioTestStatus,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
)
