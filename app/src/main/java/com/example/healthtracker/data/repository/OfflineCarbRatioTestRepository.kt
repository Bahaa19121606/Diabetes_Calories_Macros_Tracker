package com.example.healthtracker.data.repository

import com.example.healthtracker.data.local.dao.CarbRatioTestDao
import com.example.healthtracker.data.mapper.toDomain
import com.example.healthtracker.data.mapper.toEntity
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import com.example.healthtracker.domain.repository.CarbRatioTestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineCarbRatioTestRepository @Inject constructor(
    private val dao: CarbRatioTestDao,
) : CarbRatioTestRepository {

    override suspend fun start(test: CarbRatioTest): Long = dao.insert(test.toEntity())

    override suspend fun update(test: CarbRatioTest) = dao.update(test.toEntity())

    override suspend fun get(id: Long): CarbRatioTest? = dao.getById(id)?.toDomain()

    override fun observeActive(): Flow<CarbRatioTest?> =
        dao.observeLatestWithStatus(CarbRatioTestStatus.IN_PROGRESS).map { it?.toDomain() }

    override fun observeAll(): Flow<List<CarbRatioTest>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
