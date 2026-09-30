package com.example.healthtracker.domain.timer

import java.time.Duration
import java.time.Instant

enum class TimerTrigger { PRE_MEAL_GLUCOSE, NOVORAPID_DOSE }

data class PostMealTimer(
    val fireAt: Instant,
    val trigger: TimerTrigger,
    /** Id of the glucose reading or insulin dose that started the timer. */
    val sourceId: Long,
)

object PostMealTimerPolicy {
    val DURATION: Duration = Duration.ofHours(2)

    /** Don't offer a reminder that would fire (almost) immediately, e.g. for a back-dated entry. */
    private val MIN_REMAINING: Duration = Duration.ofMinutes(1)

    /** Timer measured from when the event happened, or null if it would already be due. */
    fun timerFor(eventAt: Instant, trigger: TimerTrigger, sourceId: Long, now: Instant): PostMealTimer? {
        val fireAt = eventAt.plus(DURATION)
        return if (Duration.between(now, fireAt) >= MIN_REMAINING) PostMealTimer(fireAt, trigger, sourceId) else null
    }
}
