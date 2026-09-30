package com.example.healthtracker.domain.model

/** Macronutrients as returned by the AI parser. Gram values are whole grams. */
data class Macros(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
) {
    init {
        require(calories >= 0 && proteinGrams >= 0 && carbsGrams >= 0 && fatGrams >= 0) {
            "Macro values must be non-negative: $this"
        }
    }

    operator fun plus(other: Macros) = Macros(
        calories = calories + other.calories,
        proteinGrams = proteinGrams + other.proteinGrams,
        carbsGrams = carbsGrams + other.carbsGrams,
        fatGrams = fatGrams + other.fatGrams,
    )

    companion object {
        val ZERO = Macros(0, 0, 0, 0)
    }
}
