package com.example.healthtracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colors that change between the light and dark themes. */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardHigh: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val violet: Color,
    val cyan: Color,
    val sleepAccent: Color,
    val onSleepAccent: Color,
    val inRange: Color,
    val warning: Color,
    val urgent: Color,
    val carbs: Color,
    val protein: Color,
    val fat: Color,
    val scrim: Color,
)

/** True black background (OLED), deep gray cards with hairline borders. */
val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardHigh = Color(0xFF2C2C2E),
    border = Color(0xFF3A3A3C),
    text = Color(0xFFF2F2F7),
    textMuted = Color(0xFF9A9AA2),
    violet = Color(0xFF8B5CF6),
    cyan = Color(0xFF22D3EE),
    sleepAccent = Color(0xFFA5B4FC),
    onSleepAccent = Color(0xFF191970),
    inRange = Color(0xFF2DD4BF),
    warning = Color(0xFFFBBF24),
    urgent = Color(0xFFFF6B6B),
    carbs = Color(0xFF60A5FA),
    protein = Color(0xFFF87171),
    fat = Color(0xFFFACC15),
    scrim = Color(0xD1000000),
)

/** Soft gray background with white cards; accents are deepened so they stay readable on white. */
val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFE9E9EF),
    border = Color(0xFFD9D9E0),
    text = Color(0xFF111114),
    textMuted = Color(0xFF6B6B74),
    violet = Color(0xFF7C3AED),
    cyan = Color(0xFF0891B2),
    sleepAccent = Color(0xFF4F46E5),
    onSleepAccent = Color(0xFFFFFFFF),
    inRange = Color(0xFF0D9488),
    warning = Color(0xFFD97706),
    urgent = Color(0xFFE11D48),
    carbs = Color(0xFF2563EB),
    protein = Color(0xFFDC2626),
    fat = Color(0xFFCA8A04),
    scrim = Color(0xE6F2F2F7),
)

internal val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

object AppColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.background
    val Card: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.card
    val CardHigh: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.cardHigh
    val Border: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.border
    val Text: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.text
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textMuted
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.scrim
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalAppPalette.current.isDark

    // AI actions: electric violet → soft cyan.
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.violet
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.cyan
    val AiGradient: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(Violet, Cyan))

    // Sleep. The night cards stay midnight blue in both themes.
    val Midnight = Color(0xFF191970)
    val Indigo = Color(0xFF4B0082)
    val NightText = Color(0xFFF2F2F7)
    val NightAccent = Color(0xFFA5B4FC)
    val SleepGradient = Brush.linearGradient(listOf(Color(0xFF14124A), Midnight))
    val SleepAccent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.sleepAccent
    val OnSleepAccent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onSleepAccent

    // Medical data.
    val InRange: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.inRange
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.warning
    val Urgent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.urgent

    // Macros.
    val Carbs: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.carbs
    val Protein: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.protein
    val Fat: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.fat
}
