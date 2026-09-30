package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface InsulinRepository {
    /** Returns the generated id. */
    suspend fun log(dose: InsulinDose): Long
    suspend fun update(dose: InsulinDose)
    suspend fun delete(dose: InsulinDose)
    suspend fun get(id: Long): InsulinDose?
    fun observeAll(): Flow<List<InsulinDose>>
    /** Half-open range [from, to), newest first. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<InsulinDose>>
    fun observeLatest(type: InsulinType): Flow<InsulinDose?>
    fun observeTotalUnitsBetween(type: InsulinType, from: Instant, to: Instant): Flow<Double>
}
