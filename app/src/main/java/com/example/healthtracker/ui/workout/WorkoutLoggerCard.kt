package com.example.healthtracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.model.WorkoutIntensity
import com.example.healthtracker.ui.common.Card
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.OccurredAtField
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.label

/** Workouts matter to calibration: they're excluded from basal analysis and halve corrections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutLoggerCard(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SnackbarMessageEffect(state.message, snackbarHostState, viewModel::messageShown) { message ->
        when (message) {
            is WorkoutMessage.Logged -> SnackbarSpec("Logged ${message.workout.durationMinutes} min ${message.workout.intensity.label.lowercase()}")
            WorkoutMessage.SaveFailed -> SnackbarSpec("Couldn't save the workout. Please try again.")
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Workout", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutIntensity.entries.forEach { intensity ->
                    FilterChip(
                        selected = state.intensity == intensity,
                        onClick = { viewModel.onIntensityChange(intensity) },
                        label = { Text(intensity.label) },
                    )
                }
            }
            if (state.intensity == WorkoutIntensity.HEAVY_LIFTING) {
                Text(
                    "Corrections within 6 hours afterwards are halved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberField(
                value = state.duration,
                onValueChange = viewModel::onDurationChange,
                label = "Duration",
                suffix = "min",
                error = state.durationError,
                outOfRangeText = "Enter 1–600 minutes",
                enabled = !state.isSaving,
                onImeAction = viewModel::log,
                modifier = Modifier.fillMaxWidth(),
            )
            OccurredAtField(
                label = "Started",
                value = state.startedAt,
                error = state.timeError,
                onValueChange = viewModel::onStartedAtChange,
                enabled = !state.isSaving,
            )
            PrimaryButton("Log workout", onClick = viewModel::log, enabled = !state.isSaving)
        }
    }
}

@Composable
fun WorkoutLogScreen(snackbarHostState: SnackbarHostState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        WorkoutLoggerCard(snackbarHostState)
    }
}
