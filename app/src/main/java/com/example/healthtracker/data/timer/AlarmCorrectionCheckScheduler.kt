package com.example.healthtracker.data.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.notification.CorrectionCheckAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the pending check so it survives process death and can be re-armed after a reboot. */
@Singleton
class AlarmCorrectionCheckScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: ExactAlarms,
) : CorrectionCheckScheduler {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _activeCheck = MutableStateFlow(load())
    override val activeCheck: StateFlow<CorrectionCheck?> = _activeCheck.asStateFlow()

    override fun schedule(check: CorrectionCheck): ScheduleResult {
        prefs.edit {
            putLong(KEY_DOSE_ID, check.doseId)
            putLong(KEY_DOSED_AT, check.dosedAt.toEpochMilli())
        }
        _activeCheck.value = check
        return alarms.set(check.fireAt, alarmIntent())
    }

    /** After reboot / update. A past-due check fires immediately. */
    fun rearm() {
        _activeCheck.value?.let { alarms.set(it.fireAt, alarmIntent()) }
    }

    /** Called by the receiver: the reminder has been shown. */
    fun fired() = clear()

    override fun cancel() {
        alarms.cancel(alarmIntent())
        clear()
    }

    private fun clear() {
        prefs.edit { clear() }
        _activeCheck.value = null
    }

    private fun load(): CorrectionCheck? {
        val doseId = prefs.getLong(KEY_DOSE_ID, NONE).takeIf { it != NONE } ?: return null
        return CorrectionCheck(doseId, Instant.ofEpochMilli(prefs.getLong(KEY_DOSED_AT, 0)))
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CorrectionCheckAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val PREFS_NAME = "correction_check"
        const val KEY_DOSE_ID = "dose_id"
        const val KEY_DOSED_AT = "dosed_at"
        const val NONE = -1L
        const val REQUEST_CODE = 2_030
    }
}
