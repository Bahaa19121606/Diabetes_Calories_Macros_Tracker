package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.CarbRatioTest
import kotlinx.coroutines.flow.Flow

interface CarbRatioTestRepository {
    suspend fun start(test: CarbRatioTest): Long
    suspend fun update(test: CarbRatioTest)
    suspend fun get(id: Long): CarbRatioTest?
    /** At most one test is in progress at a time. */
    fun observeActive(): Flow<CarbRatioTest?>
    /** Newest first. */
    fun observeAll(): Flow<List<CarbRatioTest>>
}
