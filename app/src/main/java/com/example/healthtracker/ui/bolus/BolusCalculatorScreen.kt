package com.example.healthtracker.ui.bolus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.calibration.BolusNote
import com.example.healthtracker.domain.calibration.BolusResult
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.CardTitle
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.formatCarbRatio
import com.example.healthtracker.ui.common.formatCorrectionFactor
import com.example.healthtracker.ui.common.formatGlucose
import com.example.healthtracker.ui.common.formatGlucoseValue
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.formatTime
import com.example.healthtracker.ui.common.glucoseRangeText
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.meal.DelayedRiseWarning
import com.example.healthtracker.ui.t1d.CorrectionCheckDialog
import com.example.healthtracker.ui.t1d.StartTimerDialog
import com.example.healthtracker.ui.t1d.rememberReminderPermissionState
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.HugeNumber
import com.example.healthtracker.ui.timeline.isSlowAbsorbing
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun BolusCalculatorScreen(
    snackbarHostState: SnackbarHostState,
    onDone: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: BolusCalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberReminderPermissionState()
    // Whichever reminder was offered is scheduled once the permission dialog closes.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions.refresh()
        if (viewModel.uiState.value.correctionPrompt != null) viewModel.confirmCorrectionCheck() else viewModel.confirmTimer()
    }
    fun confirmWithPermission(confirm: () -> Unit) {
        if (permissions.needsNotificationRuntimeRequest) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            confirm()
        }
    }

    SnackbarMessageEffect(state.message, snackbarHostState, viewModel::messageShown) { message ->
        when (message) {
            is BolusMessage.Logged -> SnackbarSpec("Logged ${formatInsulinUnits(message.units)} u NovoRapid")
            is BolusMessage.TimerScheduled -> reminderSpec(message.fireAt, message.exact, permissions::openExactAlarmSettings)
            is BolusMessage.CorrectionCheckScheduled -> reminderSpec(message.fireAt, message.exact, permissions::openExactAlarmSettings)
            BolusMessage.SaveFailed -> SnackbarSpec("Couldn't save. Please try again.")
        }
    }
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InputsCard(state, viewModel, onOpenProfile)
        if (state.meal?.isSlowAbsorbing == true) DelayedRiseWarning()

        when (val result = state.result) {
            null -> MutedText("Enter carbs, your current glucose, or both.", Modifier.padding(horizontal = 4.dp))
            is BolusResult.TreatLow -> TreatLowCard(result.glucoseMgDl, state.glucoseUnit)
            is BolusResult.Suggestion -> {
                MathCard(result, state)
                DoseCard(state = state, onAdjust = viewModel::onAdjustmentChange, onLog = viewModel::logDose)
                result.notes.forEach { note ->
                    Text(
                        noteText(note, result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (note == BolusNote.LARGE_DOSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    state.timerPrompt?.let { prompt ->
        StartTimerDialog(
            prompt = prompt,
            replacesExisting = false,
            onConfirm = { confirmWithPermission(viewModel::confirmTimer) },
            onDismiss = viewModel::dismissTimerPrompt,
        )
    }
    state.correctionPrompt?.let { prompt ->
        CorrectionCheckDialog(
            fireAt = prompt.fireAt,
            onConfirm = { confirmWithPermission(viewModel::confirmCorrectionCheck) },
            onDismiss = viewModel::dismissCorrectionPrompt,
        )
    }
}

private fun reminderSpec(fireAt: Instant, exact: Boolean, openSettings: () -> Unit) = if (exact) {
    SnackbarSpec("Reminder set for ${formatTime(fireAt)}")
} else {
    SnackbarSpec("Reminder set for ${formatTime(fireAt)}, but it may arrive late.", "Allow", openSettings)
}

@Composable
private fun InputsCard(state: BolusUiState, viewModel: BolusCalculatorViewModel, onOpenProfile: () -> Unit) {
    AppCard {
        CardTitle("NovoRapid dose")
        state.meal?.let { MutedText("For: ${it.description.lineSequence().first()}") }
        NumberField(
            value = state.form.carbs,
            onValueChange = viewModel::onCarbsChange,
            label = "Carbs",
            suffix = "g",
            error = state.form.carbsError,
            outOfRangeText = "Enter 0–1000 g",
            hint = "Leave empty for a correction only",
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = state.form.glucose,
            onValueChange = viewModel::onGlucoseChange,
            label = "Current glucose",
            suffix = state.glucoseUnit.label,
            error = state.form.glucoseError,
            outOfRangeText = "Enter a value between ${glucoseRangeText(state.glucoseUnit)}",
            hint = state.form.glucoseSource?.let { "From your reading at ${formatTime(it.measuredAt)}" }
                ?: "Will be logged with the dose",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedText(
                "Ratio ${formatCarbRatio(state.profile.carbRatio)} · 1 u per " +
                    "${formatCorrectionFactor(state.profile.correctionFactor, state.glucoseUnit)} · " +
                    "target ${formatGlucose(state.profile.targetMgDl, state.glucoseUnit)}",
                Modifier.weight(1f),
            )
            TextButton(onClick = onOpenProfile) { Text("Change") }
        }
    }
}

/** Never a magic number: every step of the calculation, one row each. */
@Composable
private fun MathCard(suggestion: BolusResult.Suggestion, state: BolusUiState) {
    val unit = state.glucoseUnit
    val profile = state.profile
    AppCard(spacing = 10.dp) {
        CardTitle("How it's calculated")
        val carbs = state.carbsUsed ?: 0
        if (carbs > 0) {
            MathRow("Meal", "$carbs g ÷ ${formatCarbRatio(profile.carbRatio).removePrefix("1:")} (your carb ratio)", units(suggestion.mealUnits))
        }
        val notes = suggestion.notes
        val glucose = state.glucoseUsedMgDl
        when {
            BolusNote.NO_GLUCOSE in notes || glucose == null -> MathRow("Correction", "no glucose entered", units(0.0))
            BolusNote.GLUCOSE_STALE in notes -> MathRow("Correction", "reading over 15 min old", units(0.0))
            else -> {
                val raw = (glucose - profile.targetMgDl) / profile.correctionFactor
                MathRow(
                    "Correction",
                    "(${formatGlucoseValue(glucose, unit)} − ${formatGlucoseValue(profile.targetMgDl, unit)} target) ÷ " +
                        "${formatGlucoseValue(profile.correctionFactor.roundToInt(), unit)} (your factor)",
                    signedUnits(raw),
                )
                if (BolusNote.CORRECTION_BLOCKED_RECENT_BOLUS in notes) {
                    MathRow("Skipped", "NovoRapid less than 3 h ago", signedUnits(-raw))
                } else if (raw > 0) {
                    val iobSubtracted = BolusNote.INSULIN_ON_BOARD_SUBTRACTED in notes
                    val afterIob = if (iobSubtracted) (raw - suggestion.insulinOnBoard).coerceAtLeast(0.0) else raw
                    if (iobSubtracted) MathRow("Active insulin", "still working from an earlier dose", signedUnits(afterIob - raw))
                    if (BolusNote.HALVED_AFTER_WORKOUT in notes) MathRow("Heavy lifting", "correction halved", signedUnits(-afterIob / 2))
                }
            }
        }
        HorizontalDivider(color = AppColors.Border)
        val exact = (suggestion.mealUnits + suggestion.correctionUnits).coerceAtLeast(0.0)
        MathRow("Total", "rounded to whole units", "${oneDecimal(exact)} → ${formatInsulinUnits(suggestion.totalUnits)} u", bold = true)
    }
}

@Composable
private fun MathRow(label: String, formula: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(formula, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = if (bold) FontWeight.Black else FontWeight.Bold)
    }
}

/** The dose, huge and glowing, with a slider to dial it up or down and a log button. */
@Composable
private fun DoseCard(state: BolusUiState, onAdjust: (Int) -> Unit, onLog: () -> Unit) {
    val finalUnits = state.finalUnits ?: 0.0
    val adjustment = state.form.adjustment
    val suggested = formatInsulinUnits(state.suggestedUnits ?: 0.0)
    val accent = if (state.isAdjusted) AppColors.Warning else MaterialTheme.colorScheme.primary
    val max = BolusCalculatorViewModel.MAX_ADJUSTMENT
    AppCard(spacing = 8.dp) {
        Box(
            Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    formatInsulinUnits(finalUnits),
                    style = HugeNumber.copy(shadow = Shadow(accent.copy(alpha = 0.9f), blurRadius = 48f)),
                    color = accent,
                )
                Text(unitWord(finalUnits), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            when {
                adjustment > 0 -> "You added $adjustment u to the suggested $suggested u"
                adjustment < 0 -> "You took ${-adjustment} u off the suggested $suggested u"
                else -> "Slide to adjust for exercise or anything the calculator doesn't know"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isAdjusted) AppColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = adjustment.toFloat(),
            onValueChange = { onAdjust(it.roundToInt()) },
            valueRange = -max.toFloat()..max.toFloat(),
            steps = max * 2 - 1,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = AppColors.CardHigh),
        )
        Row(Modifier.fillMaxWidth()) {
            MutedText("−$max u", Modifier.weight(1f))
            MutedText("0")
            Text(
                "+$max u",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.isAdjusted) MutedText("An adjusted dose doesn't count toward the ratio suggestions.")
        PrimaryButton(
            "Log ${formatInsulinUnits(finalUnits)} ${unitWord(finalUnits)}",
            onClick = onLog,
            enabled = !state.isSaving && finalUnits > 0,
            containerColor = accent,
            contentColor = if (state.isAdjusted) Color.Black else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun TreatLowCard(mgDl: Int, unit: GlucoseUnit) {
    AppCard(color = MaterialTheme.colorScheme.errorContainer) {
        Text("Treat the low first", style = MaterialTheme.typography.headlineSmall, color = AppColors.Urgent)
        Text(formatGlucoseValue(mgDl, unit), style = HugeNumber, color = AppColors.Urgent)
        Text(
            "Below ${formatGlucose(70, unit)}. Take 15 g of fast carbs, recheck in 15 minutes, " +
                "and don't take NovoRapid until you're back in range.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun unitWord(units: Double) = if (units == 1.0) "unit" else "units"

private fun oneDecimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

private fun units(value: Double) = "${oneDecimal(value)} u"

private fun signedUnits(value: Double): String {
    val rounded = round(value * 10) / 10
    val sign = if (rounded > 0) "+" else if (rounded < 0) "−" else ""
    return sign + String.format(Locale.getDefault(), "%.1f u", abs(rounded))
}

private fun noteText(note: BolusNote, suggestion: BolusResult.Suggestion): String = when (note) {
    BolusNote.NO_GLUCOSE -> "No glucose entered: meal dose only."
    BolusNote.GLUCOSE_STALE -> "Your reading is over 15 minutes old: measure again to include a correction."
    BolusNote.CORRECTION_BLOCKED_RECENT_BOLUS ->
        "No correction: you took NovoRapid at ${suggestion.lastNovorapidAt?.let { formatTime(it) } ?: "recently"}, " +
            "less than 3 hours ago. Correcting now risks stacking insulin."
    BolusNote.BELOW_TARGET_REDUCED -> "Below target: the meal dose is reduced."
    BolusNote.INSULIN_ON_BOARD_SUBTRACTED -> "Insulin still active from your last dose was taken off the correction."
    BolusNote.HALVED_AFTER_WORKOUT -> "Heavy lifting in the last 6 hours: the correction is halved."
    BolusNote.LARGE_DOSE -> "This is a large dose. Double-check the carbs and glucose before injecting."
}
