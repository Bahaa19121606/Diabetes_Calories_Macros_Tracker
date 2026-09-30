package com.example.healthtracker.domain.model

import java.time.Duration
import java.time.Instant

data class SleepSession(
    val id: Long = 0,
    val bedtime: Instant,
    val wakeTime: Instant,
) {
    init {
        require(wakeTime.isAfter(bedtime)) { "Wake time must be after bedtime" }
        require(duration <= MAX_DURATION) { "Sleep can't be longer than $MAX_DURATION" }
    }

    val duration: Duration get() = Duration.between(bedtime, wakeTime)

    companion object {
        val MAX_DURATION: Duration = Duration.ofHours(20)
    }
}
