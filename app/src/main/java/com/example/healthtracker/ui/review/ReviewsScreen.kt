package com.example.healthtracker.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.calibration.GlucoseLimits
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.review.DailyGlucose
import com.example.healthtracker.domain.review.GlucoseSummary
import com.example.healthtracker.domain.review.HbA1cEstimate
import com.example.healthtracker.domain.review.HbA1cEstimator
import com.example.healthtracker.domain.review.PeriodRange
import com.example.healthtracker.domain.review.PeriodSummary
import com.example.healthtracker.domain.review.ReviewPeriod
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.CardTitle
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.PillToggle
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.ScreenTitle
import com.example.healthtracker.ui.common.SecondaryButton
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.common.formatGlucoseValue
import com.example.healthtracker.ui.common.formatInsulinUnits
import com.example.healthtracker.ui.common.glucoseColor
import com.example.healthtracker.ui.common.label
import com.example.healthtracker.ui.meal.MacroPills
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.BigNumber
import com.example.healthtracker.ui.theme.HugeNumber
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReviewsScreen(
    contentPadding: PaddingValues,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.glucoseUnit
    val periodWord = if (state.period == ReviewPeriod.WEEK) "week" else "month"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Reviews")
        HbA1cCard(state, onEdit = viewModel::openLabEditor)
        PillToggle(
            options = ReviewPeriod.entries,
            selected = state.period,
            label = { if (it == ReviewPeriod.WEEK) "Week" else "Month" },
            onSelect = viewModel::selectPeriod,
        )
        PeriodHeader(state, onPrevious = viewModel::previous, onNext = viewModel::next, onCurrent = viewModel::goToCurrent)

        val current = state.current
        when {
            current == null -> Unit
            current.isEmpty -> AppCard {
                CardTitle("Nothing logged")
                MutedText("There are no entries for this $periodWord yet. Logged meals, readings, doses and sleep show up here.")
            }
            else -> {
                current.glucose?.let { GlucoseCard(it, current, state.previous?.glucose, unit, periodWord) }
                current.nutrition?.let { nutrition ->
                    AppCard {
                        CardTitle("Food", icon = Icons.Outlined.Restaurant, tint = AppColors.Carbs)
                        BigStat("${nutrition.dailyAverage.calories}", "kcal a day on average")
                        MacroPills(nutrition.dailyAverage)
                        MutedText("Daily averages over the ${days(nutrition.daysLogged)} with meals logged.")
                    }
                }
                current.insulin?.let { insulin ->
                    AppCard {
                        CardTitle("Insulin", icon = Icons.Outlined.Vaccines, tint = AppColors.Violet)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MiniStat(
                                "NovoRapid",
                                insulin.averageDailyNovorapid?.let { "${formatInsulinUnits(it.roundToOne())} u" } ?: "–",
                                "a day",
                                Modifier.weight(1f),
                            )
                            MiniStat(
                                "Tresiba",
                                insulin.averageTresiba?.let { "${formatInsulinUnits(it.roundToOne())} u" } ?: "–",
                                if (insulin.tresibaDoses > 0) "average of ${insulin.tresibaDoses} doses" else "not logged",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
                current.sleep?.let { sleep ->
                    AppCard(brush = AppColors.SleepGradient) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = AppColors.NightAccent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Sleep", style = MaterialTheme.typography.titleMedium, color = AppColors.NightText)
                        }
                        Text(formatDuration(sleep.average), style = BigNumber, color = AppColors.NightText)
                        val previous = state.previous?.sleep
                        Text(
                            buildString {
                                append("a night on average, ${nights(sleep.nights)} logged")
                                if (previous != null) {
                                    val diff = sleep.average.toMinutes() - previous.average.toMinutes()
                                    if (diff != 0L) append(" · ${if (diff > 0) "▲" else "▼"} ${abs(diff)} min vs last $periodWord")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.NightAccent,
                        )
                        if (sleep.shortNights > 0) {
                            Text(
                                "${nights(sleep.shortNights).replaceFirstChar { it.uppercase() }} under 6 hours. Short sleep can raise glucose the next morning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.NightText,
                            )
                        }
                    }
                }
                if (current.activity.workouts > 0) {
                    AppCard {
                        CardTitle("Activity", icon = Icons.Outlined.FitnessCenter, tint = AppColors.Warning)
                        BigStat(
                            formatDuration(java.time.Duration.ofMinutes(current.activity.totalMinutes.toLong())),
                            "across ${current.activity.workouts} workout${if (current.activity.workouts == 1) "" else "s"}",
                        )
                    }
                }
            }
        }
    }

    state.labForm?.let { form ->
        LabHbA1cDialog(
            form = form,
            today = state.today,
            onValueChange = viewModel::onLabValueChange,
            onDateChange = viewModel::onLabDateChange,
            onSave = viewModel::saveLab,
            onDismiss = viewModel::dismissLabEditor,
        )
    }
}

@Composable
private fun PeriodHeader(state: ReviewsUiState, onPrevious: () -> Unit, onNext: () -> Unit, onCurrent: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(periodTitle(state), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(periodSubtitle(state.period, state.range), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
        IconButton(onClick = onNext, enabled = !state.isCurrentPeriod, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
        }
    }
    if (!state.isCurrentPeriod) {
        TextButton(onClick = onCurrent, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.period == ReviewPeriod.WEEK) "Back to this week" else "Back to this month")
        }
    }
}

@Composable
private fun GlucoseCard(
    glucose: GlucoseSummary,
    summary: PeriodSummary,
    previous: GlucoseSummary?,
    unit: GlucoseUnit,
    periodWord: String,
) {
    AppCard {
        CardTitle("Glucose", icon = Icons.Outlined.WaterDrop, tint = AppColors.InRange)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatGlucoseValue(glucose.averageMgDl, unit), style = HugeNumber, color = glucoseColor(glucose.averageMgDl))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 14.dp)) {
                Text(unit.label, style = MaterialTheme.typography.titleMedium)
                Text("average", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }
        if (previous != null) {
            val diff = glucose.averageMgDl - previous.averageMgDl
            MutedText(
                if (diff == 0) {
                    "Same average as last $periodWord"
                } else {
                    "${if (diff > 0) "▲" else "▼"} ${formatGlucoseValue(abs(diff), unit)} ${unit.label} vs last $periodWord · " +
                        "time in range ${glucose.inRangePercent}% (was ${previous.inRangePercent}%)"
                },
            )
        }
        TimeInRangeBar(glucose)
        DailyGlucoseChart(summary.range, summary.daily, unit)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniStat("Readings", "${glucose.readings}", "logged", Modifier.weight(1f))
            MiniStat(
                "Lows",
                "${glucose.lows}",
                "under ${formatGlucoseValue(GlucoseLimits.LOW_MG_DL, unit)}",
                Modifier.weight(1f),
                color = if (glucose.lows > 0) AppColors.Urgent else null,
            )
            MiniStat(
                "Fasting",
                glucose.averageFastingMgDl?.let { formatGlucoseValue(it, unit) } ?: "–",
                "average",
                Modifier.weight(1f),
                color = glucose.averageFastingMgDl?.let { glucoseColor(it) },
            )
        }
        MutedText(
            "From finger-prick readings, so the percentages describe your checks, not every hour of the day. " +
                "Lowest ${formatGlucoseValue(glucose.lowestMgDl, unit)}, highest ${formatGlucoseValue(glucose.highestMgDl, unit)} ${unit.label}.",
        )
    }
}

/** Low / in range / high as one segmented bar, with the percentages underneath. */
@Composable
private fun TimeInRangeBar(glucose: GlucoseSummary) {
    val segments = listOf(
        Triple(glucose.lowPercent, AppColors.Urgent, "Low"),
        Triple(glucose.inRangePercent, AppColors.InRange, "In range"),
        Triple(glucose.highPercent, AppColors.Warning, "High"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            segments.filter { it.first > 0 }.forEach { (percent, color, _) ->
                Box(Modifier.weight(percent.toFloat()).fillMaxHeight().background(color))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            segments.forEach { (percent, color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text("$label ", style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
                    Text("$percent%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** One column per day: the day's lowest to highest reading, with a dot at its average. */
@Composable
private fun DailyGlucoseChart(range: PeriodRange, daily: List<DailyGlucose>, unit: GlucoseUnit) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = AppColors.TextMuted)
    val bandColor = AppColors.InRange.copy(alpha = 0.12f)
    val lineColor = AppColors.InRange.copy(alpha = 0.5f)
    val rangeColor = AppColors.Border
    val byDate = daily.associateBy { it.date }
    val dotColors = daily.associate { it.date to glucoseColor(it.averageMgDl) }
    val maxMgDl = maxOf(300, daily.maxOfOrNull { it.highestMgDl } ?: 0)
    val minMgDl = 40
    val isWeek = range.days <= 7

    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val labelSpace = 18.dp.toPx()
        val axisSpace = 30.dp.toPx()
        val chartHeight = size.height - labelSpace
        val chartWidth = size.width - axisSpace
        fun y(mgDl: Int) = chartHeight * (1f - (mgDl.coerceIn(minMgDl, maxMgDl) - minMgDl).toFloat() / (maxMgDl - minMgDl))

        // Target band, 70–180.
        val top = y(GlucoseLimits.HIGH_MG_DL)
        val bottom = y(GlucoseLimits.LOW_MG_DL)
        drawRect(bandColor, Offset(axisSpace, top), Size(chartWidth, bottom - top))
        listOf(GlucoseLimits.LOW_MG_DL, GlucoseLimits.HIGH_MG_DL).forEach { limit ->
            drawLine(lineColor, Offset(axisSpace, y(limit)), Offset(size.width, y(limit)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val text = textMeasurer.measure(formatGlucoseValue(limit, unit), labelStyle)
            drawText(text, topLeft = Offset(0f, y(limit) - text.size.height / 2))
        }

        val slot = chartWidth / range.days
        val stroke = if (isWeek) 10.dp.toPx() else 5.dp.toPx()
        for (i in 0 until range.days) {
            val date = range.start.plusDays(i.toLong())
            val x = axisSpace + slot * (i + 0.5f)
            byDate[date]?.let { day ->
                if (day.highestMgDl != day.lowestMgDl) {
                    drawLine(rangeColor, Offset(x, y(day.lowestMgDl)), Offset(x, y(day.highestMgDl)), stroke, cap = StrokeCap.Round)
                }
                drawCircle(dotColors.getValue(date), radius = stroke * 0.65f, center = Offset(x, y(day.averageMgDl)))
            }
            val showLabel = isWeek || date.dayOfMonth == 1 || date.dayOfMonth % 7 == 1
            if (showLabel) {
                val label = if (isWeek) date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) else "${date.dayOfMonth}"
                val text = textMeasurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(x - text.size.width / 2, size.height - text.size.height))
            }
        }
    }
}

@Composable
private fun HbA1cCard(state: ReviewsUiState, onEdit: () -> Unit) {
    val estimate = state.estimate
    val lab = state.lab
    AppCard {
        CardTitle("Estimated HbA1c", icon = Icons.Outlined.Science, tint = AppColors.Violet)
        if (estimate == null || lab == null) {
            MutedText(
                "Enter your latest lab HbA1c and the app will estimate how it's changing from the readings you log.",
            )
            PrimaryButton("Add lab result", onClick = onEdit, icon = Icons.Outlined.Bloodtype)
            return@AppCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("≈ ${formatPercent(estimate.percent)}", style = HugeNumber, color = hbA1cColor(estimate.percent))
            Text("%", style = BigNumber, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 14.dp)) {
                Text("${estimate.mmolPerMol} mmol/mol", style = MaterialTheme.typography.titleSmall)
                Text(confidenceLabel(estimate.confidence), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }
        val change = estimate.change
        Text(
            buildString {
                append("Lab result: ${formatPercent(lab.percent)}% on ${LAB_DATE.format(lab.measuredOn)}")
                when {
                    change > 0 -> append(" · ▲ ${formatPercent(change)} since")
                    change < 0 -> append(" · ▼ ${formatPercent(-change)} since")
                    else -> append(" · no change since")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        MutedText(estimateBasis(estimate))
        if (HbA1cEstimator.daysSinceLab(lab, state.today) > HbA1cEstimator.RETEST_AFTER_DAYS) {
            MutedText("Your lab result is over 3 months old. A new lab test gives the estimate a fresh starting point.")
        }
        EstimateWarning()
        SecondaryButton("Update lab result", onClick = onEdit, icon = Icons.Outlined.Bloodtype, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EstimateWarning() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(AppColors.Warning.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "This is only an estimate, not a lab result. Finger-prick checks miss the highs and lows between them, " +
                "so the real value can differ. Don't change your treatment based on it; only a lab test measures HbA1c.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun estimateBasis(estimate: HbA1cEstimate): String {
    val share = (estimate.loggedShare * 100).roundToInt()
    return buildString {
        if (share == 0) {
            append("No readings logged since the lab test yet, so the estimate equals the lab result.")
        } else {
            append("$share% of the estimate comes from your readings since the lab test; the rest assumes glucose stayed where the lab result put it.")
            append(" ${estimate.recentReadings} readings in the last 30 days.")
        }
        if (estimate.calibrated) append(" Adjusted for the times you usually check, using readings from before the lab test.")
    }
}

private fun confidenceLabel(confidence: HbA1cEstimate.Confidence) = when (confidence) {
    HbA1cEstimate.Confidence.LOW -> "Rough estimate"
    HbA1cEstimate.Confidence.MEDIUM -> "Fair estimate"
    HbA1cEstimate.Confidence.HIGH -> "Good estimate"
}

@Composable
private fun hbA1cColor(percent: Double): Color = when {
    percent < 7.0 -> AppColors.InRange
    percent < 8.0 -> AppColors.Warning
    else -> AppColors.Urgent
}

@Composable
private fun BigStat(value: String, caption: String) {
    Column {
        Text(value, style = BigNumber)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

@Composable
private fun MiniStat(label: String, value: String, caption: String, modifier: Modifier = Modifier, color: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color ?: Color.Unspecified, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted, maxLines = 2)
    }
}

private val LAB_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private fun periodTitle(state: ReviewsUiState): String {
    val current = state.period.rangeContaining(state.today)
    val last = state.period.shift(current, -1)
    return when (state.period) {
        ReviewPeriod.WEEK -> when (state.range) {
            current -> "This week"
            last -> "Last week"
            else -> "${DAY_MONTH.format(state.range.start)} – ${DAY_MONTH.format(state.range.lastDay)}"
        }
        ReviewPeriod.MONTH -> when (state.range) {
            current -> "This month"
            last -> "Last month"
            else -> MONTH_YEAR.format(state.range.start)
        }
    }
}

private fun periodSubtitle(period: ReviewPeriod, range: PeriodRange): String = when (period) {
    ReviewPeriod.WEEK -> "${DAY_MONTH.format(range.start)} – ${DAY_MONTH.format(range.lastDay)}"
    ReviewPeriod.MONTH -> MONTH_YEAR.format(range.start)
}

private fun formatPercent(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun Double.roundToOne(): Double = (this * 10).roundToInt() / 10.0

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

private fun nights(n: Int) = if (n == 1) "1 night" else "$n nights"
