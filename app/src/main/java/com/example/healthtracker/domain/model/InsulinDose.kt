package com.example.healthtracker.domain.model

import java.time.Instant

enum class InsulinType(val isRapidActing: Boolean) {
    NOVORAPID(isRapidActing = true),
    TRESIBA(isRapidActing = false),
}

data class InsulinDose(
    val id: Long = 0,
    val type: InsulinType,
    /** Fractional for older or manual entries; the calculator suggests whole units. */
    val units: Double,
    val administeredAt: Instant,
    /** Optional link to the meal this bolus covered. */
    val mealId: Long? = null,
    val note: String? = null,
    /** How a NovoRapid dose was worked out, when it came from the dose calculator. */
    val bolus: BolusBreakdown? = null,
) {
    init {
        require(units > 0.0) { "Insulin units must be positive, was $units" }
    }
}

/**
 * The inputs behind a calculated NovoRapid dose. Recorded so the meal-response analysis can
 * tell which carb ratio a meal actually tested, and how much of the dose was a correction.
 */
data class BolusBreakdown(
    val carbsGrams: Int,
    /** Grams of carbohydrate covered by one unit (the "12" in 1:12). */
    val carbRatio: Double,
    /** Correction part of the dose; negative when the meal dose was reduced for a below-target reading. */
    val correctionUnits: Double,
    /** mg/dL per unit used for the correction; null on doses logged before it was recorded. */
    val correctionFactor: Double? = null,
)
