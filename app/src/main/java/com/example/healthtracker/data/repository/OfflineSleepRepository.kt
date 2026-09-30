package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.SleepSessionDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class OfflineSleepRepository @Inject constructor(
    private val dao: SleepSessionDao,
) : SleepRepository {

    override suspend fun log(session: SleepSession): Long = dao.insert(session.toEntity())

    override suspend fun delete(session: SleepSession) = dao.delete(session.toEntity())

    override fun observeOverlapping(from: Instant, to: Instant): Flow<List<SleepSession>> =
        dao.observeOverlapping(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }
}
