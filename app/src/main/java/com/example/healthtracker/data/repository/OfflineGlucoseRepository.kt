package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.GlucoseReadingDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.repository.GlucoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class OfflineGlucoseRepository @Inject constructor(
    private val dao: GlucoseReadingDao,
) : GlucoseRepository {

    override suspend fun log(reading: GlucoseReading): Long = dao.insert(reading.toEntity())

    override suspend fun update(reading: GlucoseReading) = dao.update(reading.toEntity())

    override suspend fun delete(reading: GlucoseReading) = dao.delete(reading.toEntity())

    override suspend fun get(id: Long): GlucoseReading? = dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<GlucoseReading>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<GlucoseReading>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    override fun observeLatest(): Flow<GlucoseReading?> = dao.observeLatest().map { it?.toDomain() }

    override suspend fun getLatest(context: GlucoseContext): GlucoseReading? =
        dao.getLatestWithContext(context)?.toDomain()
}
