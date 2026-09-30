package com.example.healthtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.ui.AppViewModel
import com.example.healthtracker.ui.HealthTrackerRoot
import com.example.healthtracker.ui.theme.HealthTrackerTheme
import com.example.healthtracker.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            // The window background shows for the few milliseconds the setting takes to load.
            themeMode?.let { mode ->
                val dark = mode.isDark()
                // Status and navigation bar icons follow the app's theme, not the phone's.
                DisposableEffect(dark) {
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                HealthTrackerTheme(darkTheme = dark) {
                    HealthTrackerRoot()
                }
            }
        }
    }
}
