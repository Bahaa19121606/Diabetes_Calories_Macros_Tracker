package com.example.healthtracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.healthtracker.R
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.navigation.DeepLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostMealNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Idempotent; called at app start so the channel exists before the user looks for it in Settings. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.post_meal_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.post_meal_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showPostMealReminder(timer: PostMealTimer) {
        ensureChannel()
        if (!ReminderPermissions.canPostNotifications(context)) return

        val openLogScreen = Intent(Intent.ACTION_VIEW, DeepLinks.logGlucose(GlucoseContext.POST_MEAL))
            .setPackage(context.packageName)
            // CLEAR_TASK: start from a fresh task so Navigation builds the back stack in
            // place instead of restarting the activity (it does that for NEW_TASK alone).
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openLogScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucose)
            .setContentTitle(context.getString(R.string.post_meal_notification_title))
            .setContentText(context.getString(R.string.post_meal_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(timer.fireAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify; nothing else we can do from a receiver.
        }
    }

    /** One notification, replaced at each checkpoint, opening the test screen. */
    fun showCarbTestReminder(hoursSinceStart: Long) {
        ensureChannel()
        if (!ReminderPermissions.canPostNotifications(context)) return

        val openTest = Intent(Intent.ACTION_VIEW, DeepLinks.carbTest())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            CARB_TEST_REQUEST_CODE,
            openTest,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucose)
            .setContentTitle(context.getString(R.string.carb_test_notification_title, hoursSinceStart))
            .setContentText(context.getString(R.string.carb_test_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(CARB_TEST_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify.
        }
    }

    fun showCorrectionCheckReminder(fireAt: Instant) {
        ensureChannel()
        if (!ReminderPermissions.canPostNotifications(context)) return

        val openLogScreen = Intent(Intent.ACTION_VIEW, DeepLinks.logGlucose(GlucoseContext.OTHER))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            CORRECTION_REQUEST_CODE,
            openLogScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucose)
            .setContentTitle(context.getString(R.string.correction_check_notification_title))
            .setContentText(context.getString(R.string.correction_check_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(fireAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(CORRECTION_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify.
        }
    }

    private companion object {
        const val CHANNEL_ID = "post_meal_reminders"
        const val NOTIFICATION_ID = 2_001
        const val REQUEST_CODE = 2_002
        const val CARB_TEST_NOTIFICATION_ID = 2_020
        const val CARB_TEST_REQUEST_CODE = 2_021
        const val CORRECTION_NOTIFICATION_ID = 2_031
        const val CORRECTION_REQUEST_CODE = 2_032
    }
}
