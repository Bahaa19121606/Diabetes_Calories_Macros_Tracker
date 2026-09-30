package com.example.healthtracker.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.healthtracker.domain.calibration.GlucoseLimits
import com.example.healthtracker.ui.theme.AppColors

val CardShape = RoundedCornerShape(24.dp)
val HairlineBorder: BorderStroke
    @Composable @ReadOnlyComposable get() = BorderStroke(0.5.dp, AppColors.Border)

/** Minimum height of primary actions: big enough to hit with shaky hands. */
val BigTapTarget: Dp = 60.dp

/** The app's elevated surface: deep gray, 24 dp corners, a hairline border instead of a shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Card,
    brush: Brush? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .border(HairlineBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Big title at the top of a tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Drop-in replacement for Material's Card with the app's look (deep gray, 24 dp corners,
 * hairline border). Screens import this instead of androidx.compose.material3.Card.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = AppColors.Card),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = colors.containerColor,
        contentColor = colors.contentColor,
        border = HairlineBorder,
    ) {
        Column(content = content)
    }
}

/** Primary action: full width, pill-shaped, tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = BigTapTarget),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** AI actions use the violet → cyan gradient: the app is doing the heavy lifting. */
@Composable
fun AiButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = BigTapTarget)
            .clip(CircleShape)
            .background(AppColors.AiGradient)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** A pill-shaped tag with a colored dot: "● 60 g Carbs". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (emphasized) color.copy(alpha = 0.22f) else AppColors.CardHigh,
        border = BorderStroke(0.5.dp, color.copy(alpha = if (emphasized) 0.8f else 0.35f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Teal in range, amber for a mild high, coral for anything urgent (a low or very high). */
@Composable
@ReadOnlyComposable
fun glucoseColor(mgDl: Int): Color = when {
    mgDl < GlucoseLimits.LOW_MG_DL -> AppColors.Urgent
    mgDl <= GlucoseLimits.HIGH_MG_DL -> AppColors.InRange
    mgDl <= VERY_HIGH_MG_DL -> AppColors.Warning
    else -> AppColors.Urgent
}

private const val VERY_HIGH_MG_DL = 250
