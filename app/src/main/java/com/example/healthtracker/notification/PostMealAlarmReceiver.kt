package com.example.healthtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

class PostMealAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context, TimerReceiverEntryPoint::class.java)
        val store = deps.timerStore()
        // A null timer means it was cancelled after the alarm was already in flight.
        val timer = store.timer.value ?: return
        store.clear()
        deps.notifier().showPostMealReminder(timer)
    }
}
