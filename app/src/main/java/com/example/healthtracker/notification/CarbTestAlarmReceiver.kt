package com.example.healthtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.healthtracker.domain.calibration.CarbRatioTestProtocol
import dagger.hilt.android.EntryPointAccessors

class CarbTestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context, TimerReceiverEntryPoint::class.java)
        val scheduler = deps.carbTestScheduler()
        val testId = intent.getLongExtra(EXTRA_TEST_ID, -1)
        val checkpoint = intent.getIntExtra(EXTRA_CHECKPOINT, -1)
        // Ignore alarms for a test that was aborted or replaced after they were armed.
        if (testId < 0 || scheduler.activeTestId != testId) return
        val hours = CarbRatioTestProtocol.CHECKPOINTS.getOrNull(checkpoint)?.toHours() ?: return

        deps.notifier().showCarbTestReminder(hours)
        if (checkpoint == CarbRatioTestProtocol.CHECKPOINTS.lastIndex) scheduler.finished(testId)
    }

    companion object {
        const val EXTRA_TEST_ID = "test_id"
        const val EXTRA_CHECKPOINT = "checkpoint"
    }
}
