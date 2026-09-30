package com.example.healthtracker.di

import com.example.healthtracker.data.timer.AlarmCarbTestReminderScheduler
import com.example.healthtracker.data.timer.AlarmCorrectionCheckScheduler
import com.example.healthtracker.data.timer.AlarmPostMealTimerScheduler
import com.example.healthtracker.data.timer.PostMealTimerStore
import com.example.healthtracker.data.timer.SharedPrefsPostMealTimerStore
import com.example.healthtracker.domain.timer.CarbTestReminderScheduler
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TimerModule {
    @Binds abstract fun bindTimerStore(impl: SharedPrefsPostMealTimerStore): PostMealTimerStore
    @Binds abstract fun bindTimerScheduler(impl: AlarmPostMealTimerScheduler): PostMealTimerScheduler
    @Binds abstract fun bindCarbTestReminderScheduler(impl: AlarmCarbTestReminderScheduler): CarbTestReminderScheduler
    @Binds abstract fun bindCorrectionCheckScheduler(impl: AlarmCorrectionCheckScheduler): CorrectionCheckScheduler
}
