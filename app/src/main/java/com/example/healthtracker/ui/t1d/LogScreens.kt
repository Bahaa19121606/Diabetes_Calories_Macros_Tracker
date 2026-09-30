package com.example.healthtracker.ui.t1d

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.BigNumberInput
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.OccurredAtField
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.PillToggle
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.SecondaryButton
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.formatDateTime
import com.example.healthtracker.ui.common.formatGlucose
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.formatTime
import com.example.healthtracker.ui.common.glucoseColor
import com.example.healthtracker.ui.common.glucoseRangeText
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.common.message
import com.example.healthtracker.ui.common.parseDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucoseLogScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: T1dLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val unit = state.glucoseUnit
    T1dEffects(state, snackbarHostState, viewModel)

    LogColumn {
        state.activeTimer?.let { timer ->
            ActiveTimerCard("Post-meal check", timer.fireAt, rememberReminderPermissionState(), onCancel = viewModel::cancelActiveTimer)
        }
        AppCard(contentPadding = PaddingValues(vertical = 28.dp, horizontal = 20.dp)) {
            BigNumberInput(
                value = form.glucoseValue,
                onValueChange = viewModel::onGlucoseValueChange,
                unitLabel = unit.label,
                color = typedGlucoseColor(form.glucoseValue, unit),
                error = form.glucoseError?.message("Enter a value between ${glucoseRangeText(unit)}"),
                enabled = !form.isSaving,
                onDone = viewModel::logGlucose,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlucoseContext.entries.forEach { context ->
                FilterChip(
                    selected = form.glucoseContext == context,
                    onClick = { viewModel.onGlucoseContextChange(context) },
                    label = { Text(context.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        OccurredAtField(
            label = "Measured",
            value = form.glucoseTime,
            error = form.glucoseTimeError,
            onValueChange = viewModel::onGlucoseTimeChange,
            enabled = !form.isSaving,
        )
        PrimaryButton("Log reading", onClick = viewModel::logGlucose, enabled = !form.isSaving)
    }
}

@Composable
fun InsulinLogScreen(
    snackbarHostState: SnackbarHostState,
    onOpenDoseCalculator: () -> Unit,
    viewModel: T1dLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    T1dEffects(state, snackbarHostState, viewModel)
    val accent = if (form.insulinType == InsulinType.NOVORAPID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    LogColumn {
        SecondaryButton(
            "Calculate a NovoRapid dose",
            onClick = onOpenDoseCalculator,
            icon = Icons.Outlined.Calculate,
            modifier = Modifier.fillMaxWidth(),
        )
        PillToggle(
            options = InsulinType.entries,
            selected = form.insulinType,
            label = { it.label },
            onSelect = viewModel::onInsulinTypeChange,
            selectedColor = accent,
        )
        MutedText(if (form.insulinType.isRapidActing) "Rapid-acting · mealtime bolus and corrections" else "Long-acting · once-daily basal")
        AppCard(contentPadding = PaddingValues(vertical = 28.dp, horizontal = 20.dp)) {
            BigNumberInput(
                value = form.insulinUnits,
                onValueChange = viewModel::onInsulinUnitsChange,
                unitLabel = "units",
                color = accent,
                error = form.insulinUnitsError?.message("Enter a dose between 0.05 and 100 u"),
                enabled = !form.isSaving,
                onDone = viewModel::logInsulin,
            )
        }
        OccurredAtField(
            label = "Injected",
            value = form.insulinTime,
            error = form.insulinTimeError,
            onValueChange = viewModel::onInsulinTimeChange,
            enabled = !form.isSaving,
        )
        state.lastTresibaDose?.let {
            MutedText("Last Tresiba: ${formatInsulinUnits(it.units)} u · ${formatDateTime(it.administeredAt)}")
        }
        val units = (parseDecimal(form.insulinUnits, T1dLogViewModel.INSULIN_UNITS_RANGE) as? ParsedNumber.Valid)?.value
        PrimaryButton(
            if (units != null) "Log ${formatInsulinUnits(units)} ${if (units == 1.0) "unit" else "units"}" else "Log dose",
            onClick = viewModel::logInsulin,
            enabled = !form.isSaving,
            containerColor = accent,
        )
    }
}

@Composable
private fun LogColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** The typed value's range color, so a low stands out before it's even saved. */
@Composable
private fun typedGlucoseColor(text: String, unit: GlucoseUnit): Color {
    val value = (parseDecimal(text, 0.0..Double.MAX_VALUE) as? ParsedNumber.Valid)?.value ?: return Color.Unspecified
    val mgDl = unit.toMgDl(value)
    return if (mgDl in GlucoseReading.MIN_MG_DL..GlucoseReading.MAX_MG_DL) glucoseColor(mgDl) else Color.Unspecified
}

/** Snackbars and the post-meal timer prompt, shared by both log screens. */
@Composable
private fun T1dEffects(state: T1dLogUiState, snackbarHostState: SnackbarHostState, viewModel: T1dLogViewModel) {
    val permissions = rememberReminderPermissionState()
    // Asked in context, when the user actually starts a timer. The timer is set either way;
    // if denied, the active-timer card explains and links to Settings.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions.refresh()
        viewModel.confirmTimer()
    }

    SnackbarMessageEffect(state.message, snackbarHostState, viewModel::messageShown) { message ->
        when (message) {
            is T1dMessage.InsulinLogged ->
                SnackbarSpec("Logged ${formatInsulinUnits(message.units)} u ${message.type.label}")
            is T1dMessage.GlucoseLogged ->
                SnackbarSpec("Logged ${formatGlucose(message.reading.mgDl, state.glucoseUnit)} (${message.reading.context.label})")
            is T1dMessage.TimerScheduled ->
                SnackbarSpec("Reminder set for ${formatTime(message.fireAt)}")
            is T1dMessage.TimerScheduledInexact -> SnackbarSpec(
                "Reminder set for ${formatTime(message.fireAt)}, but it may arrive late without exact alarms.",
                actionLabel = "Allow",
                onAction = permissions::openExactAlarmSettings,
            )
            T1dMessage.TimerCancelled -> SnackbarSpec("Reminder cancelled")
            T1dMessage.SaveFailed -> SnackbarSpec("Couldn't save. Please try again.")
        }
    }

    state.timerPrompt?.let { prompt ->
        StartTimerDialog(
            prompt = prompt,
            replacesExisting = state.activeTimer != null,
            onConfirm = {
                if (permissions.needsNotificationRuntimeRequest) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.confirmTimer()
                }
            },
            onDismiss = viewModel::dismissTimerPrompt,
        )
    }
}
