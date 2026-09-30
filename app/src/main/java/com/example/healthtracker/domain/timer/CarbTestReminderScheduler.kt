package com.example.healthtracker.domain.timer

import java.time.Instant

/** Reminders at each checkpoint (2, 3 and 4 h) of the structured carb-ratio test. */
interface CarbTestReminderScheduler {
    /** Replaces any reminders for a previous test. Returns the least exact result of the three. */
    fun schedule(testId: Long, startedAt: Instant): ScheduleResult
    fun cancel()
}
