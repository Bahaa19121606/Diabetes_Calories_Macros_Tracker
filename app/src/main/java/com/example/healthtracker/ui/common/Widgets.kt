package com.example.healthtracker.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.HugeNumber

data class Ring(val progress: Float, val color: Color)

/** Concentric progress rings, outermost first. Progress over 1 wraps into a full ring. */
@Composable
fun ProgressRings(
    rings: List<Ring>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    gap: Dp = 4.dp,
    center: @Composable () -> Unit = {},
) {
    val animated = rings.map { ring ->
        animateFloatAsState(ring.progress.coerceIn(0f, 1f), tween(700), label = "ring").value
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            rings.forEachIndexed { index, ring ->
                val inset = stroke / 2 + index * (stroke + gap.toPx())
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(ring.color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(ring.color, -90f, 360f * animated[index], false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/**
 * A huge, centered number field for glucose and insulin: easy to read and hit with shaky hands.
 * [color] tints the number (e.g. by glucose range).
 */
@Composable
fun BigNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    unitLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    error: String? = null,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
    onDone: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = HugeNumber.copy(color = color, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Value in $unitLabel" },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text("0", style = HugeNumber, color = AppColors.Border, textAlign = TextAlign.Center)
                    }
                    inner()
                }
            },
        )
        Text(unitLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A row of large pill toggles (a friendlier segmented button). */
@Composable
fun <T> PillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = CircleShape,
                color = if (isSelected) selectedColor.copy(alpha = 0.2f) else AppColors.Card,
                border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) selectedColor else AppColors.Border),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A pulsing placeholder bar for loading states. */
@Composable
fun ShimmerBar(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shimmerX",
    )
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.CardHigh, AppColors.Violet.copy(alpha = 0.35f), AppColors.Cyan.copy(alpha = 0.25f), AppColors.CardHigh),
                    start = Offset(x * 600f, 0f),
                    end = Offset(x * 600f + 600f, 0f),
                ),
            ),
    )
}

/** A small round icon badge used by timeline rows. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
