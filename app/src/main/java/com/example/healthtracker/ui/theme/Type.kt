package com.example.healthtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.healthtracker.R

// Variable-font axes are still marked experimental in this Compose version.
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Nunito: rounded and friendly, bundled so it works offline. */
val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private val base = Typography()

private fun TextStyle.nunito(weight: FontWeight? = null) = copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

val AppTypography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.nunito(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.nunito(FontWeight.Bold),
    headlineLarge = base.headlineLarge.nunito(FontWeight.Bold),
    headlineMedium = base.headlineMedium.nunito(FontWeight.Bold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.Bold),
    titleLarge = base.titleLarge.nunito(FontWeight.Bold),
    titleMedium = base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(),
    bodyMedium = base.bodyMedium.nunito(),
    bodySmall = base.bodySmall.nunito(),
    labelLarge = base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = base.labelMedium.nunito(FontWeight.SemiBold),
    labelSmall = base.labelSmall.nunito(FontWeight.SemiBold),
)

/** Oversized numbers (glucose, doses): readable with shaky hands during a low. */
val HugeNumber = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 88.sp, lineHeight = 92.sp)
val BigNumber = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp)
