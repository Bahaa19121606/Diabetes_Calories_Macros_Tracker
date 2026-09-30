package com.example.healthtracker.ui.common

enum class InputError { EMPTY, NOT_A_NUMBER, OUT_OF_RANGE }

sealed interface ParsedNumber {
    data class Valid(val value: Double) : ParsedNumber
    data class Invalid(val error: InputError) : ParsedNumber
}

/** Accepts both "5.6" and "5,6" so comma-decimal locales work with any keyboard. */
fun parseDecimal(text: String, range: ClosedFloatingPointRange<Double>): ParsedNumber {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedNumber.Invalid(InputError.EMPTY)
    val value = trimmed.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?: return ParsedNumber.Invalid(InputError.NOT_A_NUMBER)
    return if (value in range) ParsedNumber.Valid(value) else ParsedNumber.Invalid(InputError.OUT_OF_RANGE)
}
