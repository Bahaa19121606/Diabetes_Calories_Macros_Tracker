package com.example.healthtracker.ui.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.calibration.MealResponseAnalyzer
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.CardShape
import com.example.healthtracker.ui.common.HairlineBorder
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.ProgressRings
import com.example.healthtracker.ui.common.Ring
import com.example.healthtracker.ui.common.formatDay
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.common.formatGlucoseValue
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.formatTime
import com.example.healthtracker.ui.common.glucoseColor
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.t1d.ActiveTimerCard
import com.example.healthtracker.ui.t1d.rememberReminderPermissionState
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.BigNumber
import java.time.LocalDate

@Composable
fun TimelineScreen(
    onOpenReview: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberReminderPermissionState()
    var pendingDelete by remember { mutableStateOf<TimelineEntry?>(null) }
    val unit = state.glucoseUnit

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Room for the + button.
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            DayHeader(
                date = state.date,
                isToday = state.isToday,
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
                onToday = viewModel::goToToday,
            )
        }
        item(key = "macros") { MacroHeader(state.summary.macros, state.goals) }
        item(key = "status") { StatusRow(state, unit) }
        state.activeTimer?.let { timer ->
            item(key = "timer") {
                ActiveTimerCard("Post-meal check", timer.fireAt, permissions, onCancel = viewModel::cancelTimer)
            }
        }
        state.correctionCheck?.let { check ->
            item(key = "correction") {
                ActiveTimerCard("Correction check", check.fireAt, permissions, onCancel = viewModel::cancelCorrectionCheck)
            }
        }
        if (state.showWeeklyReview) {
            item(key = "weekly") { WeeklyReviewCard(onOpenReview) }
        }

        state.wake?.let { session ->
            item(key = "wake") {
                SleepBookend(
                    icon = Icons.Outlined.WbSunny,
                    title = "Woke up at ${formatTime(session.wakeTime)}",
                    subtitle = "Slept ${formatDuration(session.duration)}",
                    onLongClick = { pendingDelete = TimelineEntry.SleepEntry(session) },
                )
            }
        }
        if (!state.isLoading && state.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.isToday) "Nothing logged yet today.\nTap + to add your first entry." else "Nothing logged on this day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        }
        items(state.entries, key = { it.key }) { entry ->
            TimelineRow(entry, unit, onLongClick = { pendingDelete = entry })
        }
        state.bedtime?.let { session ->
            item(key = "bedtime") {
                SleepBookend(
                    icon = Icons.Outlined.Bedtime,
                    title = "Went to bed at ${formatTime(session.bedtime)}",
                    subtitle = "Slept ${formatDuration(session.duration)}",
                    onLongClick = { pendingDelete = TimelineEntry.SleepEntry(session) },
                )
            }
        }
        if (state.entries.isNotEmpty()) {
            item(key = "hint") {
                MutedText("Long-press an entry to delete it.", Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Text(
            formatDay(date, LocalDate.now()),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        if (!isToday) TextButton(onClick = onToday) { Text("Today") }
        IconButton(onClick = onNext, enabled = !isToday, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun MacroHeader(eaten: Macros, goals: Macros) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRings(
                rings = listOf(
                    Ring(eaten.carbsGrams.toFloat() / goals.carbsGrams.coerceAtLeast(1), AppColors.Carbs),
                    Ring(eaten.proteinGrams.toFloat() / goals.proteinGrams.coerceAtLeast(1), AppColors.Protein),
                    Ring(eaten.fatGrams.toFloat() / goals.fatGrams.coerceAtLeast(1), AppColors.Fat),
                ),
                modifier = Modifier.size(156.dp),
                strokeWidth = 13.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${eaten.calories}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("of ${goals.calories} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroLegend("Carbs", eaten.carbsGrams, goals.carbsGrams, AppColors.Carbs)
                MacroLegend("Protein", eaten.proteinGrams, goals.proteinGrams, AppColors.Protein)
                MacroLegend("Fat", eaten.fatGrams, goals.fatGrams, AppColors.Fat)
            }
        }
    }
}

@Composable
private fun MacroLegend(label: String, grams: Int, goal: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$grams / $goal g", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatusRow(state: TimelineUiState, unit: GlucoseUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val latest = state.latestGlucose
        StatTile(
            label = if (latest != null) "Glucose · ${formatTime(latest.measuredAt)}" else "Glucose",
            value = latest?.let { formatGlucoseValue(it.mgDl, unit) } ?: "—",
            unit = unit.label,
            color = latest?.let { glucoseColor(it.mgDl) } ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "NovoRapid",
            value = formatInsulinUnits(state.summary.novorapidUnits),
            unit = if (state.isToday) "u today" else "u",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier, spacing = 2.dp, contentPadding = PaddingValues(18.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = BigNumber, color = color, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeeklyReviewCard(onOpen: () -> Unit) {
    AppCard(brush = AppColors.SleepGradient, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Insights, contentDescription = null, tint = AppColors.NightAccent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your week in review", style = MaterialTheme.typography.titleMedium, color = AppColors.NightText)
                Text(
                    "It's Sunday: see how your glucose, food, insulin and sleep did this week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.NightAccent,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleepBookend(icon: ImageVector, title: String, subtitle: String, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppColors.SleepGradient)
            .border(HairlineBorder, CardShape)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.NightAccent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.NightText)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.NightAccent)
        }
    }
}

/** Time, a rail with a colored dot, then the event: a chronological logbook of exact events. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(entry: TimelineEntry, unit: GlucoseUnit, onLongClick: () -> Unit) {
    val accent = entry.accent()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            formatTime(entry.at),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp).padding(top = 20.dp),
        )
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.Border))
            Box(Modifier.padding(top = 22.dp).size(12.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .weight(1f)
                .clip(CardShape)
                .background(AppColors.Card)
                .border(HairlineBorder, CardShape)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntryContent(entry, unit)
            }
        }
    }
}

@Composable
private fun EntryContent(entry: TimelineEntry, unit: GlucoseUnit) {
    when (entry) {
        is TimelineEntry.MealEntry -> with(entry.meal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSlowAbsorbing) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.WarningAmber, contentDescription = "High fat or protein: delayed rise", tint = AppColors.Warning, modifier = Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniMacro("${macros.carbsGrams} g", AppColors.Carbs)
                MiniMacro("${macros.proteinGrams} g", AppColors.Protein)
                MiniMacro("${macros.fatGrams} g", AppColors.Fat)
                Text("${macros.calories} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is TimelineEntry.InsulinEntry -> with(entry.dose) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatInsulinUnits(units), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(" u ${type.label}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 3.dp))
            }
            val detail = bolus?.let { b ->
                if (b.carbsGrams > 0) "Calculator · ${b.carbsGrams} g carbs" else "Calculator · correction"
            } ?: note ?: if (type.isRapidActing) "Bolus" else "Basal"
            MutedText(detail)
        }
        is TimelineEntry.GlucoseEntry -> with(entry.reading) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatGlucoseValue(mgDl, unit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = glucoseColor(mgDl))
                Text(" ${unit.label}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 3.dp))
            }
            MutedText(listOfNotNull(context.label, note).joinToString(" · "))
        }
        is TimelineEntry.WorkoutEntry -> with(entry.workout) {
            Text(intensity.label, style = MaterialTheme.typography.titleSmall)
            MutedText("$durationMinutes min")
        }
        is TimelineEntry.SleepEntry -> with(entry.session) {
            Text("Sleep", style = MaterialTheme.typography.titleSmall)
            MutedText("${formatTime(bedtime)}–${formatTime(wakeTime)} · ${formatDuration(duration)}")
        }
    }
}

@Composable
private fun MiniMacro(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private val Meal.title: String get() = description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/** Fat and protein slow absorption: glucose may rise hours later than usual. */
val Meal.isSlowAbsorbing: Boolean
    get() = macros.fatGrams > MealResponseAnalyzer.MAX_FAT_GRAMS || macros.proteinGrams > MealResponseAnalyzer.MAX_PROTEIN_GRAMS

@Composable
private fun TimelineEntry.accent(): Color = when (this) {
    is TimelineEntry.MealEntry -> AppColors.Carbs
    is TimelineEntry.InsulinEntry -> if (dose.type == InsulinType.NOVORAPID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    is TimelineEntry.GlucoseEntry -> glucoseColor(reading.mgDl)
    is TimelineEntry.WorkoutEntry -> AppColors.Warning
    is TimelineEntry.SleepEntry -> AppColors.SleepAccent
}

private fun TimelineEntry.icon(): ImageVector = when (this) {
    is TimelineEntry.MealEntry -> Icons.Outlined.Restaurant
    is TimelineEntry.InsulinEntry -> Icons.Outlined.Vaccines
    is TimelineEntry.GlucoseEntry -> Icons.Outlined.WaterDrop
    is TimelineEntry.WorkoutEntry -> Icons.Outlined.FitnessCenter
    is TimelineEntry.SleepEntry -> Icons.Outlined.Bedtime
}
