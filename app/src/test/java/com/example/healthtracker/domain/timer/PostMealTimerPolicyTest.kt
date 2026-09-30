package com.example.healthtracker.domain.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PostMealTimerPolicyTest {

    private val now = Instant.parse("2026-09-29T12:00:00Z")

    @Test
    fun firesTwoHoursAfterTheEvent() {
        val timer = PostMealTimerPolicy.timerFor(now, TimerTrigger.NOVORAPID_DOSE, sourceId = 7, now = now)
        assertThat(timer).isEqualTo(PostMealTimer(now.plus(Duration.ofHours(2)), TimerTrigger.NOVORAPID_DOSE, 7))
    }

    @Test
    fun backdatedEvent_countsFromEventTime() {
        val eventAt = now.minus(Duration.ofMinutes(45))
        val timer = PostMealTimerPolicy.timerFor(eventAt, TimerTrigger.PRE_MEAL_GLUCOSE, sourceId = 1, now = now)
        assertThat(timer?.fireAt).isEqualTo(now.plus(Duration.ofMinutes(75)))
    }

    @Test
    fun noTimerWhenAlmostOrAlreadyDue() {
        for (ago in listOf(Duration.ofSeconds(7_170), Duration.ofHours(3))) {
            assertThat(PostMealTimerPolicy.timerFor(now.minus(ago), TimerTrigger.NOVORAPID_DOSE, 1, now)).isNull()
        }
    }
}
