package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.MealDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class OfflineMealRepository @Inject constructor(
    private val dao: MealDao,
) : MealRepository {

    override suspend fun add(meal: Meal): Long = dao.insert(meal.toEntity())

    override suspend fun update(meal: Meal) = dao.update(meal.toEntity())

    override suspend fun delete(meal: Meal) = dao.delete(meal.toEntity())

    override suspend fun get(id: Long): Meal? = dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<Meal>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Meal>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    override fun observeTotalsBetween(from: Instant, to: Instant): Flow<Macros> =
        dao.observeTotalsBetween(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
}
