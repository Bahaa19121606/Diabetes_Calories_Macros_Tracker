package com.example.healthtracker.ui.common

import java.time.Instant

enum class TimeError { IN_FUTURE }

/**
 * Resolves the user's chosen time for a log entry: null means "now". Returns null if the
 * chosen time is in the future (back-dating is allowed, forward-dating is not).
 */
fun resolveOccurredAt(chosen: Instant?, now: Instant): Instant? = when {
    chosen == null -> now
    chosen.isAfter(now) -> null
    else -> chosen
}
