package com.example.healthtracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.settings.ThemeMode
import com.example.healthtracker.ui.AppViewModel
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.Card
import com.example.healthtracker.ui.common.CardTitle
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.PillToggle
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.ScreenTitle
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec
import com.example.healthtracker.ui.common.formatCarbRatio
import com.example.healthtracker.ui.common.formatCorrectionFactor
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.label
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: ProfileViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    goalsViewModel: DailyGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.glucoseUnit

    SnackbarMessageEffect(if (state.saved) Unit else null, snackbarHostState, viewModel::savedShown) {
        SnackbarSpec("Profile saved")
    }
    val goals by goalsViewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    SnackbarMessageEffect(if (goals.saved) Unit else null, snackbarHostState, goalsViewModel::savedShown) {
        SnackbarSpec("Daily goals saved")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings")
        AppCard {
            CardTitle("Appearance")
            MutedText("System follows your phone's dark theme setting.")
            PillToggle(
                options = ThemeMode.entries,
                selected = themeMode ?: SettingsRepository.DEFAULT_THEME_MODE,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                },
                onSelect = appViewModel::setThemeMode,
            )
        }
        AppCard {
            CardTitle("Glucose unit")
            MutedText("Every glucose value in the app is shown and entered in this unit.")
            PillToggle(
                options = GlucoseUnit.entries,
                selected = unit,
                label = { it.label },
                onSelect = appViewModel::setGlucoseUnit,
            )
        }
        GoalsCard(goals, goalsViewModel)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NovoRapid settings", style = MaterialTheme.typography.titleMedium)
                NumberField(
                    value = state.form.carbRatio,
                    onValueChange = viewModel::onCarbRatioChange,
                    label = "Carb ratio: grams per unit",
                    suffix = "g / u",
                    error = state.form.carbRatioError,
                    outOfRangeText = "Enter 3–50 g per unit",
                    hint = "1 unit covers this many grams of carbs (the 12 in 1:12)",
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    value = state.form.correctionFactor,
                    onValueChange = viewModel::onCorrectionFactorChange,
                    label = "Correction factor: drop per unit",
                    suffix = "${unit.label} / u",
                    error = state.form.correctionFactorError,
                    outOfRangeText = "Enter a realistic value (5–200 mg/dL per unit)",
                    hint = "How much 1 unit lowers your glucose. Starts at 50 mg/dL; the Insights tab suggests changes.",
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    value = state.form.target,
                    onValueChange = viewModel::onTargetChange,
                    label = "Correction target",
                    suffix = unit.label,
                    error = state.form.targetError,
                    outOfRangeText = "Enter a target between 90 and 180 mg/dL",
                    onImeAction = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isEditing) TextButton(onClick = viewModel::discardChanges) { Text("Discard") }
                    Button(onClick = viewModel::save, enabled = state.isEditing) { Text("Save") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From your insulin log", style = MaterialTheme.typography.titleMedium)
                Text("Tresiba: " + (state.tresibaDose?.let { "${formatInsulinUnits(it)} u (your latest logged dose)" } ?: "not logged yet"))
                val stats = state.dailyStats
                if (stats == null) {
                    Text(
                        "Log your doses for at least 3 days to see your total daily dose and the formula estimates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Total daily dose: ${formatInsulinUnits(stats.totalDailyDose.roundToInt().toDouble())} u (average of ${stats.days} days)")
                    Text("Basal share: ${(stats.basalShare * 100).roundToInt()}% Tresiba")
                    Text(
                        "Formula starting points (to be confirmed by testing):",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    state.rule500Ratio?.let { Text("500 rule → carb ratio ${formatCarbRatio(it.roundToInt().toDouble())}") }
                    state.rule1800FactorMgDl?.let { factor ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("1800 rule → 1 u per ${formatCorrectionFactor(factor, unit)}", Modifier.weight(1f))
                            OutlinedButton(onClick = viewModel::useRule1800Factor) { Text("Use") }
                        }
                    }
                    if (state.basalHeavy) {
                        Text(
                            "Tresiba is over 55% of your daily insulin. These formulas assume about 50%, so they may " +
                                "overstate how much NovoRapid you need; it can also mean the basal dose is on the high side. " +
                                "Check the overnight analysis before changing anything.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Text(
            "Suggestions in this app are based only on what you log. Review dose changes with your diabetes care team.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalsCard(state: GoalsUiState, viewModel: DailyGoalsViewModel) {
    AppCard {
        CardTitle("Daily goals")
        MutedText("The progress rings on the Today screen fill up toward these.")
        Goal.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { goal ->
                    NumberField(
                        value = state.values[goal].orEmpty(),
                        onValueChange = { viewModel.onChange(goal, it) },
                        label = goal.label,
                        suffix = goal.suffix,
                        error = state.errors[goal],
                        outOfRangeText = "${goal.range.start.toInt()}–${goal.range.endInclusive.toInt()}",
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.isEditing) PrimaryButton("Save goals", onClick = viewModel::save)
    }
}
