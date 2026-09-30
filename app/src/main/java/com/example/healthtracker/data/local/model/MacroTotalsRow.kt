package com.example.healthtracker.data.local.model

import androidx.room.ColumnInfo

/** Projection for aggregate macro queries. */
data class MacroTotalsRow(
    val calories: Int,
    @ColumnInfo(name = "protein_g") val proteinGrams: Int,
    @ColumnInfo(name = "carbs_g") val carbsGrams: Int,
    @ColumnInfo(name = "fat_g") val fatGrams: Int,
)
