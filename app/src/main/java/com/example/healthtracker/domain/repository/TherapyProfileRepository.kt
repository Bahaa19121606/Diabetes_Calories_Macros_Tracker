package com.example.healthtracker.domain.repository

import com.example.healthtracker.domain.model.TherapyProfile
import kotlinx.coroutines.flow.Flow

interface TherapyProfileRepository {
    val profile: Flow<TherapyProfile>
    suspend fun update(profile: TherapyProfile)
}
