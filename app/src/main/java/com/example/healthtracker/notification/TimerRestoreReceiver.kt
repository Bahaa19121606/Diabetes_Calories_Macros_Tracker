package com.example.healthtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

class TimerRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val deps = EntryPointAccessors.fromApplication(context, TimerReceiverEntryPoint::class.java)
        deps.timerScheduler().rearm()
        deps.carbTestScheduler().rearm()
        deps.correctionCheckScheduler().rearm()
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Granting exact alarms after scheduling upgrades an inexact timer to an exact one.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
