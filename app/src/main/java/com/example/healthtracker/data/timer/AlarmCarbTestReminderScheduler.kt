package com.example.healthtracker.data.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.example.healthtracker.domain.calibration.CarbRatioTestProtocol
import com.example.healthtracker.domain.timer.CarbTestReminderScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.notification.CarbTestAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the running test so alarms can be re-armed after a reboot, and so cancelled alarms can be ignored. */
@Singleton
class AlarmCarbTestReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: ExactAlarms,
) : CarbTestReminderScheduler {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Id of the test whose reminders are armed, or null. */
    val activeTestId: Long? get() = prefs.getLong(KEY_TEST_ID, NONE).takeIf { it != NONE }

    override fun schedule(testId: Long, startedAt: Instant): ScheduleResult {
        cancel()
        prefs.edit {
            putLong(KEY_TEST_ID, testId)
            putLong(KEY_STARTED_AT, startedAt.toEpochMilli())
        }
        return arm(testId, startedAt, notBefore = null)
    }

    /** After reboot: re-arms the checkpoints that are still ahead. */
    fun rearm(now: Instant = Instant.now()) {
        val testId = activeTestId ?: return
        val startedAt = Instant.ofEpochMilli(prefs.getLong(KEY_STARTED_AT, 0))
        arm(testId, startedAt, notBefore = now)
    }

    /** Called when the last checkpoint has fired. */
    fun finished(testId: Long) {
        if (activeTestId == testId) prefs.edit { clear() }
    }

    override fun cancel() {
        CarbRatioTestProtocol.CHECKPOINTS.indices.forEach { alarms.cancel(alarmIntent(it, NONE)) }
        prefs.edit { clear() }
    }

    private fun arm(testId: Long, startedAt: Instant, notBefore: Instant?): ScheduleResult {
        var result = ScheduleResult.EXACT
        CarbRatioTestProtocol.CHECKPOINTS.forEachIndexed { index, offset ->
            val fireAt = startedAt.plus(offset)
            if (notBefore == null || fireAt.isAfter(notBefore)) {
                if (alarms.set(fireAt, alarmIntent(index, testId)) == ScheduleResult.INEXACT_PERMISSION_MISSING) {
                    result = ScheduleResult.INEXACT_PERMISSION_MISSING
                }
            }
        }
        return result
    }

    // One PendingIntent per checkpoint (distinct request codes); extras don't affect identity,
    // so cancel() can match them with any test id.
    private fun alarmIntent(checkpointIndex: Int, testId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_BASE + checkpointIndex,
        Intent(context, CarbTestAlarmReceiver::class.java)
            .putExtra(CarbTestAlarmReceiver.EXTRA_TEST_ID, testId)
            .putExtra(CarbTestAlarmReceiver.EXTRA_CHECKPOINT, checkpointIndex),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val PREFS_NAME = "carb_test_reminders"
        const val KEY_TEST_ID = "test_id"
        const val KEY_STARTED_AT = "started_at"
        const val NONE = -1L
        const val REQUEST_CODE_BASE = 2_010
    }
}
