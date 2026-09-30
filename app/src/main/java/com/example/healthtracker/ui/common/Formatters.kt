package com.example.healthtracker.ui.common

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.WorkoutIntensity
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val GlucoseUnit.label: String
    get() = when (this) {
        GlucoseUnit.MG_DL -> "mg/dL"
        GlucoseUnit.MMOL_L -> "mmol/L"
    }

val InsulinType.label: String
    get() = when (this) {
        InsulinType.NOVORAPID -> "Novorapid"
        InsulinType.TRESIBA -> "Tresiba"
    }

val GlucoseContext.label: String
    get() = when (this) {
        GlucoseContext.PRE_MEAL -> "Pre-meal"
        GlucoseContext.POST_MEAL -> "Post-meal"
        GlucoseContext.FASTING -> "Fasting"
        GlucoseContext.BEDTIME -> "Bedtime"
        GlucoseContext.OTHER -> "Other"
    }

/** Number only, in the user's locale: "101" or "5.6" / "5,6". */
fun formatGlucoseValue(mgDl: Int, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_DL -> mgDl.toString()
    GlucoseUnit.MMOL_L -> String.format(Locale.getDefault(), "%.1f", unit.fromMgDl(mgDl))
}

fun formatGlucose(mgDl: Int, unit: GlucoseUnit): String = "${formatGlucoseValue(mgDl, unit)} ${unit.label}"

/** Plausible input range shown in validation messages. */
fun glucoseRangeText(unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_DL -> "20–600 mg/dL"
    GlucoseUnit.MMOL_L -> "1.1–33.3 mmol/L"
}

/** "4.5", "20", "0.25". */
fun formatInsulinUnits(units: Double): String = DecimalFormat("0.##").format(units)

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormatter.format(instant.atZone(zone))

fun formatDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> shortDateFormatter.format(date)
}

/** "Today, 13:05" / "Yesterday, 22:00" / "Mon, Sep 28, 08:15". */
fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
    "${formatDay(instant.atZone(zone).toLocalDate(), today)}, ${formatTime(instant, zone)}"

/** "7 h 30 min", "45 min", "8 h". */
fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** "1 h 23 min", "5 min", "< 1 min". */
fun formatRemaining(remaining: Duration): String {
    val totalMinutes = remaining.toMinutes()
    return when {
        totalMinutes < 1 -> "< 1 min"
        totalMinutes < 60 -> "$totalMinutes min"
        else -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
    }
}

val WorkoutIntensity.label: String
    get() = when (this) {
        WorkoutIntensity.HEAVY_LIFTING -> "Heavy lifting"
        WorkoutIntensity.CARDIO -> "Cardio"
        WorkoutIntensity.LIGHT -> "Light"
    }

/** "1:12" or "1:7.5" (1 unit per N grams). */
fun formatCarbRatio(gramsPerUnit: Double): String =
    "1:" + if (gramsPerUnit % 1.0 == 0.0) gramsPerUnit.toInt().toString() else DecimalFormat("0.#").format(gramsPerUnit)

/** Correction factor per unit in the display unit: "25 mg/dL" or "1.4 mmol/L". */
fun formatCorrectionFactor(mgDlPerUnit: Double, unit: GlucoseUnit): String =
    formatGlucose(mgDlPerUnit.roundToInt(), unit)

/** Signed glucose change in the display unit: "+45 mg/dL", "−2.5 mmol/L". */
fun formatGlucoseChange(deltaMgDl: Int, unit: GlucoseUnit): String {
    val sign = if (deltaMgDl > 0) "+" else if (deltaMgDl < 0) "−" else "±"
    return "$sign${formatGlucose(abs(deltaMgDl), unit)}"
}
