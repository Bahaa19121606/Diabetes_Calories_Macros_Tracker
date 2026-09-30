package com.example.healthtracker.domain.model

import java.time.Instant

data class Meal(
    val id: Long = 0,
    /** The raw recipe / meal text the user entered and the AI analysed. */
    val description: String,
    val macros: Macros,
    /** When the meal was eaten. */
    val eatenAt: Instant,
)
