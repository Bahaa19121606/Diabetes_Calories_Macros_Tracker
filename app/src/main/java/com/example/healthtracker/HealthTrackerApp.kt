package com.example.healthtracker

import android.app.Application
import com.example.healthtracker.notification.PostMealNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HealthTrackerApp : Application() {

    @Inject lateinit var postMealNotifier: PostMealNotifier

    override fun onCreate() {
        super.onCreate()
        postMealNotifier.ensureChannel()
    }
}
