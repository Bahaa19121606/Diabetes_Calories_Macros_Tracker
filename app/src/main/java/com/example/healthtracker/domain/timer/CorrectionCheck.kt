package com.example.healthtracker.domain.timer

import com.example.healthtracker.domain.calibration.CorrectionFactorAnalyzer
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant

/** A reminder to measure glucose ~3 h after a correction-only dose, when NovoRapid has done most of its work. */
data class CorrectionCheck(val doseId: Long, val dosedAt: Instant) {
    val fireAt: Instant get() = dosedAt.plus(CorrectionCheckPolicy.DELAY)
}

object CorrectionCheckPolicy {
    val DELAY: Duration = CorrectionFactorAnalyzer.FOLLOW_UP_IDEAL
    private val MIN_REMAINING: Duration = Duration.ofMinutes(1)

    /** Null if the reminder would already be due (e.g. a back-dated dose). */
    fun checkFor(doseId: Long, dosedAt: Instant, now: Instant): CorrectionCheck? =
        CorrectionCheck(doseId, dosedAt).takeIf { Duration.between(now, it.fireAt) >= MIN_REMAINING }

    /** A reading at least this long after the dose counts as the check, so the reminder isn't needed any more. */
    fun isFollowUp(check: CorrectionCheck, readingAt: Instant): Boolean =
        !readingAt.isBefore(check.dosedAt.plus(CorrectionFactorAnalyzer.FOLLOW_UP_EARLIEST))
}

/** A single pending correction check; scheduling a new one replaces the old. */
interface CorrectionCheckScheduler {
    val activeCheck: StateFlow<CorrectionCheck?>
    fun schedule(check: CorrectionCheck): ScheduleResult
    fun cancel()
}
