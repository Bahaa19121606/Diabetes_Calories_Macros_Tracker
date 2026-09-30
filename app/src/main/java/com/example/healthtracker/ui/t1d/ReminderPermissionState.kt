package com.example.healthtracker.ui.t1d

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.healthtracker.notification.ReminderPermissions

/** Observable snapshot of the OS permissions the reminder depends on; refreshed on every resume. */
@Stable
class ReminderPermissionState(private val context: Context) {
    var notificationsAllowed by mutableStateOf(ReminderPermissions.canPostNotifications(context))
        private set
    var exactAlarmsAllowed by mutableStateOf(ReminderPermissions.canScheduleExactAlarms(context))
        private set

    val needsNotificationRuntimeRequest: Boolean
        get() = ReminderPermissions.needsNotificationRuntimeRequest(context)

    fun refresh() {
        notificationsAllowed = ReminderPermissions.canPostNotifications(context)
        exactAlarmsAllowed = ReminderPermissions.canScheduleExactAlarms(context)
    }

    fun openNotificationSettings() = start(ReminderPermissions.notificationSettingsIntent(context))

    fun openExactAlarmSettings() {
        ReminderPermissions.exactAlarmSettingsIntent(context)?.let(::start)
    }

    private fun start(intent: Intent) {
        context.startActivity(intent)
    }
}

@Composable
fun rememberReminderPermissionState(): ReminderPermissionState {
    val context = LocalContext.current
    val state = remember(context) { ReminderPermissionState(context) }
    // The user may have just come back from the system settings page.
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }
    return state
}
