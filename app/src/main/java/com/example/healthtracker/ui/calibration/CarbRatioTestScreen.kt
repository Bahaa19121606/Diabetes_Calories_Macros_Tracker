package com.example.healthtracker.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.calibration.CarbRatioTestProtocol
import com.example.healthtracker.domain.calibration.PreconditionStatus
import com.example.healthtracker.domain.calibration.TestGrade
import com.example.healthtracker.domain.calibration.TestOutcome
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.ui.common.Card
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.formatCarbRatio
import com.example.healthtracker.ui.common.formatGlucose
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.formatTime
import com.example.healthtracker.ui.common.glucoseRangeText
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.t1d.rememberReminderPermissionState
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun CarbRatioTestScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: CarbRatioTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberReminderPermissionState()
    var confirmAbort by remember { mutableStateOf(false) }
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }

    SnackbarMessageEffect(state.message, snackbarHostState, viewModel::messageShown) { message ->
        when (message) {
            is CarbTestMessage.Started -> if (message.exactReminders) {
                SnackbarSpec("Test started. You'll be reminded at 2, 3 and 4 hours.")
            } else {
                SnackbarSpec("Test started, but reminders may arrive late.", "Allow", permissions::openExactAlarmSettings)
            }
            CarbTestMessage.Aborted -> SnackbarSpec("Test stopped")
            is CarbTestMessage.RatioApplied -> SnackbarSpec("Carb ratio set to ${formatCarbRatio(message.ratio)}")
            CarbTestMessage.SaveFailed -> SnackbarSpec("Couldn't save. Please try again.")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val active = state.activeTest
        val finished = state.finished
        when {
            state.isLoading -> Unit
            finished != null -> ResultCard(
                finished, state.glucoseUnit, state.profile.carbRatio,
                onApply = viewModel::applyRatio, onClose = viewModel::dismissResult,
            )
            active != null -> RunningTest(
                test = active,
                state = state,
                now = now,
                opensAt = { viewModel.checkpointOpensAt(active, it) },
                onValueChange = viewModel::onCheckpointValueChange,
                onRecord = viewModel::recordCheckpoint,
                onAbort = { confirmAbort = true },
            )
            else -> Setup(state, onRatioChange = viewModel::onRatioChange, onStart = viewModel::start)
        }
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("Stop the test?") },
            text = { Text("Reminders will be cancelled and the test won't be graded.") },
            confirmButton = { TextButton(onClick = { confirmAbort = false; viewModel.abort() }) { Text("Stop test") } },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Setup(state: CarbTestUiState, onRatioChange: (String) -> Unit, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before you start", style = MaterialTheme.typography.titleMedium)
            state.preconditions.forEach { precondition ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (precondition.status) {
                        PreconditionStatus.OK -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
                        PreconditionStatus.FAILED -> Icons.Outlined.Cancel to MaterialTheme.colorScheme.error
                        PreconditionStatus.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline to MaterialTheme.colorScheme.outline
                    }
                    Icon(icon, contentDescription = precondition.status.name.lowercase(), tint = tint)
                    Spacer(Modifier.width(12.dp))
                    Text(precondition.kind.label(state.glucoseUnit), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.preconditions.any { it.status == PreconditionStatus.UNKNOWN }) {
                Text(
                    "Log two glucose readings (20–90 minutes apart, the latest within 15 minutes) on the Diabetes tab to confirm you're stable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The test", style = MaterialTheme.typography.titleMedium)
            NumberField(
                value = state.form.ratio,
                onValueChange = onRatioChange,
                label = "Ratio to test: grams per unit",
                suffix = "g / u",
                error = state.form.ratioError,
                outOfRangeText = "Enter 3–50 g per unit",
                hint = "Leave empty to test your current ${formatCarbRatio(state.profile.carbRatio)}",
                modifier = Modifier.fillMaxWidth(),
            )
            val dose = state.testDose
            Text(
                "1. Eat exactly ${CarbRatioTestProtocol.TEST_CARBS_GRAMS} g of fast carbs: white rice or white bread, no fat or protein.\n" +
                    "2. Inject ${dose?.let { "${formatInsulinUnits(it)} u" } ?: "—"} NovoRapid" +
                    (state.ratioToTest?.let { " (${CarbRatioTestProtocol.TEST_CARBS_GRAMS} g at ${formatCarbRatio(it)}, rounded down)." } ?: ".") +
                    "\n3. Don't eat, exercise or correct until the 4-hour check. If you go low, treat it: the test ends there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.startMgDl?.let {
                Text("Starting reading: ${formatGlucose(it, state.glucoseUnit)}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                Text("I've eaten and injected: start the test")
            }
        }
    }
}

@Composable
private fun RunningTest(
    test: CarbRatioTest,
    state: CarbTestUiState,
    now: Instant,
    opensAt: (Int) -> Instant,
    onValueChange: (Int, String) -> Unit,
    onRecord: (Int) -> Unit,
    onAbort: () -> Unit,
) {
    val unit = state.glucoseUnit
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Test in progress", style = MaterialTheme.typography.titleMedium)
            Text(
                "Started ${formatTime(test.startedAt)} at ${formatGlucose(test.startMgDl, unit)} · " +
                    "${test.carbsGrams} g with ${formatInsulinUnits(test.doseUnits)} u (${formatCarbRatio(test.ratioTested)})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    CarbRatioTestProtocol.CHECKPOINTS.forEachIndexed { index, offset ->
        val recorded = when (index) {
            0 -> test.mgDlAt2h
            1 -> test.mgDlAt3h
            else -> test.mgDlAt4h
        }
        val due = test.startedAt.plus(offset)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${offset.toHours()}-hour check · ${formatTime(due)}", style = MaterialTheme.typography.titleSmall)
                when {
                    recorded != null -> Text(formatGlucose(recorded, unit), style = MaterialTheme.typography.bodyLarge)
                    now.isBefore(opensAt(index)) -> Text(
                        "Opens at ${formatTime(opensAt(index))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            value = state.form.checkpointValues[index].orEmpty(),
                            onValueChange = { onValueChange(index, it) },
                            label = "Reading",
                            suffix = unit.label,
                            error = state.form.checkpointErrors[index],
                            outOfRangeText = glucoseRangeText(unit),
                            onImeAction = { onRecord(index) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onRecord(index) }) { Text("Save") }
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = onAbort, modifier = Modifier.fillMaxWidth()) { Text("Stop the test") }
}

@Composable
private fun ResultCard(
    result: GradedTest,
    unit: GlucoseUnit,
    currentRatio: Double,
    onApply: (TestGrade) -> Unit,
    onClose: () -> Unit,
) {
    val (test, grade) = result
    val container = when (grade.outcome) {
        TestOutcome.GOOD -> MaterialTheme.colorScheme.primaryContainer
        TestOutcome.LOW_DURING_TEST -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Test result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    "Start ${formatGlucose(test.startMgDl, unit)}",
                    test.mgDlAt2h?.let { "2 h ${formatGlucose(it, unit)}" },
                    test.mgDlAt3h?.let { "3 h ${formatGlucose(it, unit)}" },
                    test.mgDlAt4h?.let { "4 h ${formatGlucose(it, unit)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(grade.outcome.describe(test.ratioTested, grade.suggestedRatio, unit), style = MaterialTheme.typography.bodyMedium)
            if (grade.outcome == TestOutcome.LOW_DURING_TEST) {
                Text("Treat the low now: 15 g of fast carbs, recheck in 15 minutes.", fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                grade.suggestedRatio?.takeIf { it != currentRatio }?.let { ratio ->
                    Button(onClick = { onApply(grade) }) { Text("Use ${formatCarbRatio(ratio)}") }
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}
