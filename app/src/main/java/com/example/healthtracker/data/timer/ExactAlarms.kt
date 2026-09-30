package com.example.healthtracker.data.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.example.healthtracker.domain.timer.ScheduleResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager rather than WorkManager: WorkManager cannot guarantee exact timing under Doze,
 * whereas setExactAndAllowWhileIdle fires on time even while the device is idle.
 */
@Singleton
class ExactAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** A past-due time fires immediately. Falls back to an inexact alarm without the permission. */
    // canScheduleExact() is checked, and SecurityException covers revocation between check and call.
    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    fun set(at: Instant, operation: PendingIntent): ScheduleResult {
        val triggerAtMillis = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                return ScheduleResult.EXACT
            } catch (e: SecurityException) {
                // Fall through to the inexact alarm.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        return ScheduleResult.INEXACT_PERMISSION_MISSING
    }

    fun cancel(operation: PendingIntent) = alarmManager.cancel(operation)
}
