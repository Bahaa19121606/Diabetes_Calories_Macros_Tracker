package com.example.healthtracker.domain.model

import kotlin.math.roundToInt

enum class GlucoseUnit {
    MG_DL,
    MMOL_L;

    fun toMgDl(value: Double): Int = when (this) {
        MG_DL -> value.roundToInt()
        MMOL_L -> (value * GlucoseReading.MG_DL_PER_MMOL_L).roundToInt()
    }

    fun fromMgDl(mgDl: Int): Double = when (this) {
        MG_DL -> mgDl.toDouble()
        MMOL_L -> mgDl / GlucoseReading.MG_DL_PER_MMOL_L
    }
}
