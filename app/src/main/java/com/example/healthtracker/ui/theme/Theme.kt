package com.example.healthtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.healthtracker.domain.settings.ThemeMode

private fun darkScheme(p: AppPalette) = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    onPrimary = Color(0xFF1B0B45),
    primaryContainer = Color(0xFF2E1F5E),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF0E3A42),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = p.inRange,
    onTertiary = Color(0xFF00382F),
    error = p.urgent,
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF3B1113),
    onErrorContainer = Color(0xFFFFB4AB),
    background = p.background,
    onBackground = p.text,
    surface = p.background,
    onSurface = p.text,
    surfaceVariant = p.cardHigh,
    onSurfaceVariant = p.textMuted,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = p.card,
    surfaceContainerHigh = Color(0xFF232325),
    surfaceContainerHighest = p.cardHigh,
    outline = p.border,
    outlineVariant = Color(0xFF2A2A2C),
    inverseSurface = p.text,
    inverseOnSurface = p.background,
)

private fun lightScheme(p: AppPalette) = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2A0F5E),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5FB),
    onSecondaryContainer = Color(0xFF053742),
    tertiary = p.inRange,
    onTertiary = Color.White,
    error = p.urgent,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E6),
    onErrorContainer = Color(0xFF5C0617),
    background = p.background,
    onBackground = p.text,
    // Sheets, dialogs and menus sit on white, like the cards.
    surface = p.card,
    onSurface = p.text,
    surfaceVariant = p.cardHigh,
    onSurfaceVariant = p.textMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = p.card,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = p.cardHigh,
    outline = p.border,
    outlineVariant = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    // Chips and tags: fully pill-shaped.
    small = RoundedCornerShape(percent = 50),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val darkColors: ColorScheme = darkScheme(DarkPalette)
private val lightColors: ColorScheme = lightScheme(LightPalette)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun HealthTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors,
            typography = AppTypography,
            shapes = shapes,
            content = content,
        )
    }
}
