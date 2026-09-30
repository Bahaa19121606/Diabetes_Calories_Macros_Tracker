package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface GlucoseRepository {
    /** Returns the generated id. */
    suspend fun log(reading: GlucoseReading): Long
    suspend fun update(reading: GlucoseReading)
    suspend fun delete(reading: GlucoseReading)
    suspend fun get(id: Long): GlucoseReading?
    fun observeAll(): Flow<List<GlucoseReading>>
    /** Half-open range [from, to), newest first. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<GlucoseReading>>
    fun observeLatest(): Flow<GlucoseReading?>
    suspend fun getLatest(context: GlucoseContext): GlucoseReading?
}
