package com.example.healthtracker.domain.timer

import kotlinx.coroutines.flow.StateFlow

enum class ScheduleResult {
    EXACT,
    /** Scheduled, but Android may deliver it late because exact alarms are not permitted. */
    INEXACT_PERMISSION_MISSING,
}

/** A single active post-meal reminder; scheduling a new one replaces the old. */
interface PostMealTimerScheduler {
    val activeTimer: StateFlow<PostMealTimer?>
    fun schedule(timer: PostMealTimer): ScheduleResult
    fun cancel()
    fun canScheduleExact(): Boolean
}
