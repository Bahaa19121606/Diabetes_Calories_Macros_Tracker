package com.example.healthtracker.data.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import com.example.healthtracker.notification.PostMealAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmPostMealTimerScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: PostMealTimerStore,
    private val alarms: ExactAlarms,
) : PostMealTimerScheduler {

    override val activeTimer: StateFlow<PostMealTimer?> = store.timer

    override fun canScheduleExact(): Boolean = alarms.canScheduleExact()

    override fun schedule(timer: PostMealTimer): ScheduleResult {
        store.save(timer)
        return alarms.set(timer.fireAt, alarmIntent())
    }

    /** Re-registers the stored timer after reboot / update. A past-due timer fires immediately. */
    fun rearm(): ScheduleResult? = store.timer.value?.let { alarms.set(it.fireAt, alarmIntent()) }

    override fun cancel() {
        alarms.cancel(alarmIntent())
        store.clear()
    }

    // Constant request code + FLAG_UPDATE_CURRENT: there is only ever one timer, and
    // re-scheduling replaces the existing alarm.
    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PostMealAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 2_000
    }
}
