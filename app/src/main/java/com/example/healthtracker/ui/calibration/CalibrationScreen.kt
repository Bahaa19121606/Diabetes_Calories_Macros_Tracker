package com.example.healthtracker.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.calibration.CorrectionFactorReport
import com.example.healthtracker.domain.calibration.MealResponseReport
import com.example.healthtracker.domain.calibration.TresibaReport
import com.example.healthtracker.domain.calibration.WeeklyContext
import com.example.healthtracker.domain.calibration.WeeklyReview
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.ui.common.Card
import com.example.healthtracker.ui.common.ScreenTitle
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.formatCarbRatio
import com.example.healthtracker.ui.common.formatCorrectionFactor
import com.example.healthtracker.ui.common.formatDateTime
import com.example.healthtracker.ui.common.formatDay
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.common.formatGlucose
import com.example.healthtracker.ui.common.formatGlucoseChange
import com.example.healthtracker.ui.common.formatGlucoseValue
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.glucoseColor
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.theme.AppColors
import java.time.LocalDate

@Composable
fun CalibrationScreen(
    snackbarHostState: SnackbarHostState,
    onOpenCarbTest: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.glucoseUnit

    SnackbarMessageEffect(state.ratioApplied, snackbarHostState, viewModel::ratioAppliedShown) {
        SnackbarSpec("Carb ratio set to ${formatCarbRatio(it)}. A new 72-hour monitoring period starts now.")
    }
    SnackbarMessageEffect(state.factorApplied, snackbarHostState, viewModel::factorAppliedShown) {
        SnackbarSpec("Correction factor set to 1 u per ${formatCorrectionFactor(it, unit)}. A new 72-hour monitoring period starts now.")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Insights")
        Text(
            "Suggestions are based only on what you log. Review dose changes with your diabetes care team.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CoverageCard(state.coverage)
        state.tresiba?.let { TresibaCard(it, state.weekly, unit) }
        state.meals?.let { MealsCard(it, unit, onApply = viewModel::applyCarbRatio) }
        state.corrections?.let { CorrectionsCard(it, unit, onApply = viewModel::applyCorrectionFactor) }
        CarbTestCard(state, onOpenCarbTest)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AdviceBox(advice: Advice, action: (@Composable () -> Unit)? = null) {
    val container = when {
        advice.isUrgent -> MaterialTheme.colorScheme.errorContainer
        advice.isChange -> MaterialTheme.colorScheme.primaryContainer
        else -> AppColors.CardHigh
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(advice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(advice.detail, style = MaterialTheme.typography.bodySmall)
            action?.invoke()
        }
    }
}

@Composable
private fun CoverageCard(coverage: List<DayCoverage>) {
    SectionCard("Daily checks") {
        Text(
            "Fasting, before your biggest meal, 2 hours after it, and at bedtime. Bedtime → fasting checks Tresiba; " +
                "pre-meal → 2 hours checks NovoRapid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1.4f))
            DAILY_CHECKS.forEach {
                Text(it.shortLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
        val today = LocalDate.now()
        coverage.forEach { day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDay(day.date, today), Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
                DAILY_CHECKS.forEach { check ->
                    val done = check in day.done
                    Icon(
                        if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (done) "${check.label} done" else "${check.label} missing",
                        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val GlucoseContext.shortLabel: String
    get() = when (this) {
        GlucoseContext.FASTING -> "Fasting"
        GlucoseContext.PRE_MEAL -> "Pre"
        GlucoseContext.POST_MEAL -> "2 h"
        GlucoseContext.BEDTIME -> "Bed"
        GlucoseContext.OTHER -> "Other"
    }

@Composable
private fun TresibaCard(report: TresibaReport, weekly: WeeklyContext?, unit: GlucoseUnit) {
    SectionCard("Tresiba weekly review") {
        Text(
            "Current dose: " + (report.currentDose?.let { "${formatInsulinUnits(it)} u" } ?: "not logged") +
                (report.lastChangeAt?.let { " · changed ${formatDateTime(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        weekly?.let { WeeklyContextRow(it, unit) }
        AdviceBox(tresibaAdvice(report.recommendation, unit))
        if (weekly != null && WeeklyReview.sleepBeforeIncrease(weekly, report.recommendation)) {
            AdviceBox(sleepFirstAdvice(weekly, unit))
        }
        if (report.nights.isNotEmpty()) {
            Text("Recent nights", style = MaterialTheme.typography.labelLarge)
            report.nights.take(5).forEach { night ->
                Column {
                    Text(
                        "${formatDay(night.date, LocalDate.now())}: ${formatGlucose(night.bedtime.mgDl, unit)} → " +
                            "${formatGlucose(night.fasting.mgDl, unit)} (${formatGlucoseChange(night.changeMgDl, unit)}) · ${night.trend.label}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val notes = listOfNotNull(
                        night.exclusion?.label,
                        if (night.hadLow) "Low overnight: lowest ${formatGlucose(night.lowestMgDl, unit)}" else null,
                    )
                    notes.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Low")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Holistic context: average fasting glucose right next to average sleep. */
@Composable
private fun WeeklyContextRow(weekly: WeeklyContext, unit: GlucoseUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ContextTile(
            label = "Avg fasting · ${weekly.fastingDays} days",
            value = weekly.averageFastingMgDl?.let { formatGlucoseValue(it, unit) } ?: "—",
            unit = unit.label,
            color = weekly.averageFastingMgDl?.let { glucoseColor(it) } ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        ContextTile(
            label = "Avg sleep · ${weekly.nightsLogged} nights",
            value = weekly.averageSleep?.let { formatDuration(it) } ?: "—",
            unit = if (weekly.averageSleep == null) "log sleep with +" else "per night",
            color = if (weekly.sleepIsShort) AppColors.Warning else AppColors.SleepAccent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ContextTile(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.CardHigh)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealsCard(report: MealResponseReport, unit: GlucoseUnit, onApply: (Double) -> Unit) {
    SectionCard("NovoRapid carb ratio") {
        Text("Current ratio: ${formatCarbRatio(report.currentRatio)}", style = MaterialTheme.typography.bodyMedium)
        val proposed = report.recommendation.proposedRatio
        AdviceBox(carbRatioAdvice(report.recommendation, unit)) {
            if (proposed != null) {
                Button(onClick = { onApply(proposed) }) { Text("Use ${formatCarbRatio(proposed)}") }
            }
        }
        if (report.responses.isNotEmpty()) {
            Text("Calculator-dosed meals", style = MaterialTheme.typography.labelLarge)
            report.responses.take(6).forEach { response ->
                Column {
                    val rise = response.riseMgDl
                    Text(
                        formatDateTime(response.dose.administeredAt) + " · ${response.dose.bolus?.carbsGrams} g at " +
                            formatCarbRatio(response.dose.bolus?.carbRatio ?: report.currentRatio) +
                            (if (rise != null) " · ${formatGlucoseChange(rise, unit)} at 2 h" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    response.exclusion?.let {
                        Text("Not counted: ${it.label(unit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrectionsCard(report: CorrectionFactorReport, unit: GlucoseUnit, onApply: (Double) -> Unit) {
    SectionCard("NovoRapid correction factor") {
        Text("Current factor: 1 u per ${formatCorrectionFactor(report.currentFactor, unit)}", style = MaterialTheme.typography.bodyMedium)
        val proposed = report.recommendation.proposedFactor
        AdviceBox(correctionFactorAdvice(report.recommendation, unit)) {
            if (proposed != null) {
                Button(onClick = { onApply(proposed) }) { Text("Use 1 u per ${formatCorrectionFactor(proposed, unit)}") }
            }
        }
        if (report.responses.isNotEmpty()) {
            Text("Calculator corrections", style = MaterialTheme.typography.labelLarge)
            report.responses.take(6).forEach { response ->
                Column {
                    val before = response.before
                    val after = response.after
                    Text(
                        formatDateTime(response.dose.administeredAt) + " · ${formatInsulinUnits(response.dose.units)} u" +
                            (if (before != null && after != null) " · ${formatGlucose(before.mgDl, unit)} → ${formatGlucose(after.mgDl, unit)}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    response.exclusion?.let {
                        Text("Not counted: ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarbTestCard(state: CalibrationUiState, onOpenCarbTest: () -> Unit) {
    SectionCard("Structured carb-ratio test") {
        Text(
            "On a rest day with stable glucose and no active insulin: eat exactly 50 g of fast carbs dosed at the ratio " +
                "you want to test, then check at 2, 3 and 4 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val active = state.activeTest
        val last = state.testHistory.firstOrNull()
        if (active != null) {
            Text("Test in progress since ${formatDateTime(active.startedAt)}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenCarbTest) { Text("Open test") }
        } else {
            last?.let {
                HorizontalDivider()
                Text(
                    "Last test (${formatDateTime(it.test.startedAt)}, ${formatCarbRatio(it.test.ratioTested)}):",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(it.grade.outcome.describe(it.test.ratioTested, it.grade.suggestedRatio, state.glucoseUnit), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onOpenCarbTest) { Text("Start a test") }
        }
    }
}

