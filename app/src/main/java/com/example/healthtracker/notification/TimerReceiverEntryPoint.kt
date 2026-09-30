package com.example.healthtracker.notification

import com.example.healthtracker.data.timer.AlarmCarbTestReminderScheduler
import com.example.healthtracker.data.timer.AlarmCorrectionCheckScheduler
import com.example.healthtracker.data.timer.AlarmPostMealTimerScheduler
import com.example.healthtracker.data.timer.PostMealTimerStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Receivers use an entry point instead of @AndroidEntryPoint field injection to keep them trivially simple. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerReceiverEntryPoint {
    fun timerStore(): PostMealTimerStore
    fun timerScheduler(): AlarmPostMealTimerScheduler
    fun carbTestScheduler(): AlarmCarbTestReminderScheduler
    fun correctionCheckScheduler(): AlarmCorrectionCheckScheduler
    fun notifier(): PostMealNotifier
}
