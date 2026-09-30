package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.Workout
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkoutRepository {
    suspend fun log(workout: Workout): Long
    suspend fun delete(workout: Workout)
    /** Half-open range [from, to), newest first. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<Workout>>
}
