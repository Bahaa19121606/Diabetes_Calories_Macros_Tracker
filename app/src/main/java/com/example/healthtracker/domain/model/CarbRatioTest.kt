package com.example.healthtracker.domain.model

import java.time.Instant

enum class CarbRatioTestStatus { IN_PROGRESS, COMPLETED, ABORTED }

/** One run of the structured 50 g fast-carb test (see CarbRatioTestProtocol). */
data class CarbRatioTest(
    val id: Long = 0,
    val startedAt: Instant,
    val startMgDl: Int,
    val carbsGrams: Int,
    val ratioTested: Double,
    val doseUnits: Double,
    val mgDlAt2h: Int? = null,
    val mgDlAt3h: Int? = null,
    val mgDlAt4h: Int? = null,
    val status: CarbRatioTestStatus = CarbRatioTestStatus.IN_PROGRESS,
    val endedAt: Instant? = null,
)
