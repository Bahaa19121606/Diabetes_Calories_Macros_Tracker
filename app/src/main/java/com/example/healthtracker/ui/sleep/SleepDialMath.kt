package com.example.healthtracker.ui.sleep

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.roundToInt

/** A 24-hour dial: midnight at the top, clockwise. Times are minutes of the day. */
object SleepDialMath {
    const val MINUTES_PER_DAY = 24 * 60
    const val SNAP_MINUTES = 5

    /** Canvas angle in degrees (0 = 3 o'clock, clockwise) for a time of day. */
    fun angleFor(minutes: Int): Float = minutes * 360f / MINUTES_PER_DAY - 90f

    /** The time of day under a point at ([dx], [dy]) from the dial's center, snapped to 5 minutes. */
    fun minutesAt(dx: Float, dy: Float): Int {
        val fromTop = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90 + 360) % 360
        val snapped = (fromTop / 360 * MINUTES_PER_DAY / SNAP_MINUTES).roundToInt() * SNAP_MINUTES
        return snapped % MINUTES_PER_DAY
    }

    /** Minutes asleep going clockwise from bedtime to wake time (crossing midnight as needed). */
    fun durationMinutes(bedMinutes: Int, wakeMinutes: Int): Int =
        ((wakeMinutes - bedMinutes) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** Bedtime and wake time as instants, for a night that ended on [wakeDate]. Null for zero length. */
    fun resolve(bedMinutes: Int, wakeMinutes: Int, wakeDate: LocalDate, zone: ZoneId): Pair<Instant, Instant>? {
        val duration = durationMinutes(bedMinutes, wakeMinutes)
        if (duration == 0) return null
        val wake = wakeDate.atStartOfDay(zone).plusMinutes(wakeMinutes.toLong()).toInstant()
        return wake.minus(Duration.ofMinutes(duration.toLong())) to wake
    }
}
