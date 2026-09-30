package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.WorkoutDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class OfflineWorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {

    override suspend fun log(workout: Workout): Long = dao.insert(workout.toEntity())

    override suspend fun delete(workout: Workout) = dao.delete(workout.toEntity())

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Workout>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }
}
