package com.example.healthtracker.domain.model

import java.time.Duration
import java.time.Instant

enum class WorkoutIntensity { HEAVY_LIFTING, CARDIO, LIGHT }

data class Workout(
    val id: Long = 0,
    val startedAt: Instant,
    val durationMinutes: Int,
    val intensity: WorkoutIntensity,
    val note: String? = null,
) {
    init {
        require(durationMinutes > 0) { "Workout duration must be positive, was $durationMinutes" }
    }

    val endedAt: Instant get() = startedAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
}
