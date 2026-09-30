package com.example.healthtracker.domain.model

import java.time.Instant
import kotlin.math.roundToInt

enum class GlucoseContext { PRE_MEAL, POST_MEAL, FASTING, BEDTIME, OTHER }

/**
 * Stored canonically in mg/dL. Whole mg/dL is precise enough to round-trip any
 * mmol/L value entered to one decimal place (0.1 mmol/L ≈ 1.8 mg/dL).
 */
data class GlucoseReading(
    val id: Long = 0,
    val mgDl: Int,
    val context: GlucoseContext,
    val measuredAt: Instant,
    val mealId: Long? = null,
    val note: String? = null,
) {
    init {
        require(mgDl in MIN_MG_DL..MAX_MG_DL) { "Glucose $mgDl mg/dL is outside the plausible meter range" }
    }

    val mmolL: Double get() = mgDl / MG_DL_PER_MMOL_L

    companion object {
        const val MG_DL_PER_MMOL_L = 18.0182
        // Typical glucometer/CGM reporting limits ("LO"/"HI").
        const val MIN_MG_DL = 20
        const val MAX_MG_DL = 600

        fun fromMmolL(
            mmolL: Double,
            context: GlucoseContext,
            measuredAt: Instant,
            mealId: Long? = null,
            note: String? = null,
        ) = GlucoseReading(
            mgDl = (mmolL * MG_DL_PER_MMOL_L).roundToInt(),
            context = context,
            measuredAt = measuredAt,
            mealId = mealId,
            note = note,
        )
    }
}
