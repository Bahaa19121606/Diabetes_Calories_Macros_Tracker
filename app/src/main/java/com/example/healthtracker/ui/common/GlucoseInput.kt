package com.example.healthtracker.ui.common

import com.example.healthtracker.domain.model.GlucoseUnit
import java.util.Locale

/** Canonical text for a glucose value in [unit]: whole mg/dL, or mmol/L to one decimal. */
fun glucoseInputText(mgDl: Int, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_DL -> mgDl.toString()
    GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", unit.fromMgDl(mgDl))
}

/** Re-expresses a half-typed value when the unit is switched; leaves unparseable text alone. */
fun convertGlucoseInput(text: String, from: GlucoseUnit, to: GlucoseUnit): String {
    if (from == to) return text
    val value = (parseDecimal(text, 0.0..Double.MAX_VALUE) as? ParsedNumber.Valid)?.value ?: return text
    return glucoseInputText(from.toMgDl(value), to)
}
