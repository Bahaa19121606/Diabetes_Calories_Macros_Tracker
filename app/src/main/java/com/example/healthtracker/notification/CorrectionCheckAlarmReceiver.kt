package com.example.healthtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

class CorrectionCheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context, TimerReceiverEntryPoint::class.java)
        val scheduler = deps.correctionCheckScheduler()
        // Null: cancelled (e.g. the follow-up reading was already logged) after the alarm was in flight.
        val check = scheduler.activeCheck.value ?: return
        scheduler.fired()
        deps.notifier().showCorrectionCheckReminder(check.fireAt)
    }
}
