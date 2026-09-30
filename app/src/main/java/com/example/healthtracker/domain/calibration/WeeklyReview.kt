package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.SleepSession
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The last week's fasting glucose next to sleep: short sleep alone can raise morning glucose. */
data class WeeklyContext(
    val averageFastingMgDl: Int?,
    val fastingDays: Int,
    val averageSleep: Duration?,
    val nightsLogged: Int,
) {
    /** Enough short nights that a high fasting average may be about sleep rather than basal. */
    val sleepIsShort: Boolean
        get() = averageSleep != null && nightsLogged >= WeeklyReview.MIN_NIGHTS && averageSleep < WeeklyReview.SHORT_SLEEP
}

/**
 * Tresiba is reviewed weekly, never adjusted from a single day: adjusting basal day to day stacks
 * insulin, because each dose lasts over 24 hours.
 */
object WeeklyReview {
    val WINDOW: Duration = Duration.ofDays(7)
    val SHORT_SLEEP: Duration = Duration.ofHours(6)
    const val MIN_NIGHTS = 3
    val REVIEW_DAY: DayOfWeek = DayOfWeek.SUNDAY

    fun isReviewDay(date: LocalDate): Boolean = date.dayOfWeek == REVIEW_DAY

    fun context(readings: List<GlucoseReading>, sleeps: List<SleepSession>, now: Instant, zone: ZoneId): WeeklyContext {
        val from = now.minus(WINDOW)
        // The first fasting reading of each morning.
        val fasting = readings
            .filter { it.context == GlucoseContext.FASTING && it.measuredAt.isAfter(from) && !it.measuredAt.isAfter(now) }
            .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
            .map { (_, day) -> day.minBy { it.measuredAt }.mgDl }
        val nights = sleeps.filter { it.wakeTime.isAfter(from) && !it.wakeTime.isAfter(now) }
        return WeeklyContext(
            averageFastingMgDl = fasting.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            fastingDays = fasting.size,
            averageSleep = nights.takeIf { it.isNotEmpty() }?.let { list -> Duration.ofSeconds(list.sumOf { it.duration.seconds } / list.size) },
            nightsLogged = nights.size,
        )
    }

    /** Short sleep can explain high mornings: rest comes before a basal increase. */
    fun sleepBeforeIncrease(context: WeeklyContext, recommendation: TresibaRecommendation): Boolean =
        recommendation is TresibaRecommendation.Increase && context.sleepIsShort
}
