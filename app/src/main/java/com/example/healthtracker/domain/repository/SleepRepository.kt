package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.SleepSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    suspend fun log(session: SleepSession): Long
    suspend fun delete(session: SleepSession)
    /** Sessions overlapping [from, to), newest first. */
    fun observeOverlapping(from: Instant, to: Instant): Flow<List<SleepSession>>
}
