package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface MealRepository {
    /** Returns the generated id. */
    suspend fun add(meal: Meal): Long
    suspend fun update(meal: Meal)
    suspend fun delete(meal: Meal)
    suspend fun get(id: Long): Meal?
    fun observeAll(): Flow<List<Meal>>
    /** Half-open range [from, to), newest first. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<Meal>>
    fun observeTotalsBetween(from: Instant, to: Instant): Flow<Macros>
}
