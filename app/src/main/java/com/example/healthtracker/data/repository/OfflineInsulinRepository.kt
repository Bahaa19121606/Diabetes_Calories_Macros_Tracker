package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.InsulinDoseDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.repository.InsulinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class OfflineInsulinRepository @Inject constructor(
    private val dao: InsulinDoseDao,
) : InsulinRepository {

    override suspend fun log(dose: InsulinDose): Long = dao.insert(dose.toEntity())

    override suspend fun update(dose: InsulinDose) = dao.update(dose.toEntity())

    override suspend fun delete(dose: InsulinDose) = dao.delete(dose.toEntity())

    override suspend fun get(id: Long): InsulinDose? = dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<InsulinDose>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<InsulinDose>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    override fun observeLatest(type: InsulinType): Flow<InsulinDose?> =
        dao.observeLatestOfType(type).map { it?.toDomain() }

    override fun observeTotalUnitsBetween(type: InsulinType, from: Instant, to: Instant): Flow<Double> =
        dao.observeTotalUnitsBetween(type, from.toEpochMilli(), to.toEpochMilli())
}
