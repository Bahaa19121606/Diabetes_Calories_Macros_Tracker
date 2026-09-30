package com.example.healthtracker.ui.sleep

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.PillToggle
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.formatDay
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.BigNumber
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Handle { BED, WAKE }

@Composable
fun SleepLogScreen(
    onSaved: (SleepSession) -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) { state.saved?.let(currentOnSaved) }
    var editing by remember { mutableStateOf<Handle?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MutedText("Drag the moon to when you went to bed and the sun to when you woke up.", Modifier.fillMaxWidth())
        SleepDial(
            bedMinutes = state.bedMinutes,
            wakeMinutes = state.wakeMinutes,
            onBedChange = viewModel::onBedtimeChange,
            onWakeChange = viewModel::onWakeTimeChange,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeChip(Icons.Outlined.Bedtime, "Bedtime", state.bedMinutes, Modifier.weight(1f)) { editing = Handle.BED }
            TimeChip(Icons.Outlined.WbSunny, "Woke up", state.wakeMinutes, Modifier.weight(1f)) { editing = Handle.WAKE }
        }
        Text("Woke up on", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
        PillToggle(
            options = listOf(state.today.minusDays(1), state.today),
            selected = state.wakeDate,
            label = { formatDay(it, state.today) },
            onSelect = viewModel::onWakeDateChange,
            selectedColor = AppColors.SleepAccent,
        )
        state.error?.let {
            Text(
                when (it) {
                    SleepError.ZERO_LENGTH -> "Bedtime and wake time are the same."
                    SleepError.TOO_LONG -> "That's over ${SleepSession.MAX_DURATION.toHours()} hours: check the times."
                    SleepError.WAKE_IN_FUTURE -> "That wake time is still in the future. Did you mean yesterday?"
                    SleepError.SAVE_FAILED -> "Couldn't save. Please try again."
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            "Save ${formatDuration(Duration.ofMinutes(state.durationMinutes.toLong()))} of sleep",
            onClick = viewModel::save,
            enabled = !state.isSaving,
            containerColor = AppColors.SleepAccent,
            contentColor = AppColors.OnSleepAccent,
        )
    }

    editing?.let { handle ->
        TimeDialog(
            initialMinutes = if (handle == Handle.BED) state.bedMinutes else state.wakeMinutes,
            onDismiss = { editing = null },
            onConfirm = {
                if (handle == Handle.BED) viewModel.onBedtimeChange(it) else viewModel.onWakeTimeChange(it)
                editing = null
            },
        )
    }
}

/**
 * A 24-hour track with a moon (bedtime) and a sun (wake) handle; the duration updates live in
 * the middle. Gestures are handled by the whole dial rather than the handles, so a drag can
 * start on a handle itself or anywhere on the ring; it moves whichever handle is closest.
 */
@Composable
private fun SleepDial(
    bedMinutes: Int,
    wakeMinutes: Int,
    onBedChange: (Int) -> Unit,
    onWakeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBed by rememberUpdatedState(bedMinutes)
    val currentWake by rememberUpdatedState(wakeMinutes)
    val currentOnBedChange by rememberUpdatedState(onBedChange)
    val currentOnWakeChange by rememberUpdatedState(onWakeChange)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val trackColor = AppColors.CardHigh
    val tickColor = AppColors.Border
    val arcBrushColors = listOf(AppColors.Indigo, AppColors.SleepAccent, AppColors.Indigo)
    val haptics = LocalHapticFeedback.current
    val trackWidth = 44.dp
    val handleSize = 52.dp

    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val sizePx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) }
        val trackPx = with(density) { trackWidth.toPx() }
        val handlePx = with(density) { handleSize.toPx() }
        // Leave room for the handles, which are wider than the track.
        val radius = sizePx / 2 - handlePx / 2
        val center = Offset(sizePx / 2, sizePx / 2)

        fun pointFor(minutes: Int): Offset {
            val rad = Math.toRadians(SleepDialMath.angleFor(minutes).toDouble())
            return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
        }

        fun nearestHandle(at: Offset): Handle =
            if ((at - pointFor(currentBed)).getDistance() <= (at - pointFor(currentWake)).getDistance()) Handle.BED else Handle.WAKE

        fun move(handle: Handle, at: Offset) {
            val minutes = SleepDialMath.minutesAt(at.x - center.x, at.y - center.y)
            val current = if (handle == Handle.BED) currentBed else currentWake
            if (minutes == current) return
            // A tick of feedback on every whole hour.
            if (minutes % 60 == 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (handle == Handle.BED) currentOnBedChange(minutes) else currentOnWakeChange(minutes)
        }

        var dragging by remember { mutableStateOf<Handle?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(sizePx) {
                    detectDragGestures(
                        onDragStart = { at ->
                            dragging = nearestHandle(at)
                            move(nearestHandle(at), at)
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                        onDrag = { change, _ ->
                            change.consume()
                            dragging?.let { move(it, change.position) }
                        },
                    )
                }
                .pointerInput(sizePx) {
                    detectTapGestures { at ->
                        // Taps on the ring move the nearest handle there.
                        val distance = hypot(at.x - center.x, at.y - center.y)
                        if (distance > radius - handlePx && distance < radius + handlePx) move(nearestHandle(at), at)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawCircle(trackColor, radius, center, style = Stroke(trackPx))
                // Hour ticks and labels.
                for (hour in 0 until 24) {
                    val rad = Math.toRadians(SleepDialMath.angleFor(hour * 60).toDouble())
                    val inner = radius - trackPx / 2 - (if (hour % 6 == 0) 14.dp.toPx() else 8.dp.toPx())
                    val outer = radius - trackPx / 2 - 4.dp.toPx()
                    drawLine(
                        tickColor,
                        Offset(center.x + inner * cos(rad).toFloat(), center.y + inner * sin(rad).toFloat()),
                        Offset(center.x + outer * cos(rad).toFloat(), center.y + outer * sin(rad).toFloat()),
                        strokeWidth = if (hour % 6 == 0) 3f else 1.5f,
                    )
                    if (hour % 6 == 0) {
                        val text = textMeasurer.measure(hourLabel(hour), labelStyle)
                        val labelRadius = inner - 16.dp.toPx()
                        drawText(
                            text,
                            topLeft = Offset(
                                center.x + labelRadius * cos(rad).toFloat() - text.size.width / 2,
                                center.y + labelRadius * sin(rad).toFloat() - text.size.height / 2,
                            ),
                        )
                    }
                }
                val sweep = SleepDialMath.durationMinutes(currentBed, currentWake) * 360f / SleepDialMath.MINUTES_PER_DAY
                drawArc(
                    brush = Brush.sweepGradient(arcBrushColors, center),
                    startAngle = SleepDialMath.angleFor(currentBed),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(trackPx * 0.78f, cap = StrokeCap.Round),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val minutes = SleepDialMath.durationMinutes(bedMinutes, wakeMinutes)
                Text("${minutes / 60} h ${minutes % 60} min", style = BigNumber)
                Text("asleep", style = MaterialTheme.typography.titleSmall, color = AppColors.SleepAccent)
            }

            // Plain boxes, not Surfaces: a Surface would swallow the touch and the drag would never start.
            listOf(Handle.BED to bedMinutes, Handle.WAKE to wakeMinutes).forEach { (handle, minutes) ->
                val p = pointFor(minutes)
                val scale = if (dragging == handle) 1.15f else 1f
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset((p.x - handlePx / 2).roundToInt(), (p.y - handlePx / 2).roundToInt()) }
                        .size(handleSize)
                        .scale(scale)
                        .shadow(6.dp, CircleShape)
                        .background(if (handle == Handle.BED) AppColors.Midnight else AppColors.Warning, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (handle == Handle.BED) Icons.Outlined.Bedtime else Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = if (handle == Handle.BED) AppColors.NightAccent else AppColors.Midnight,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeChip(icon: ImageVector, label: String, minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(AppColors.Midnight), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AppColors.NightAccent, modifier = Modifier.size(16.dp))
            }
            Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = timeState) },
        confirmButton = { TextButton(onClick = { onConfirm(timeState.hour * 60 + timeState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun formatMinutes(minutes: Int): String = shortTime.format(LocalTime.of(minutes / 60, minutes % 60))

private fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0')
