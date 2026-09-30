package com.example.healthtracker.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumberInputTest {

    private val range = 0.05..100.0

    @Test
    fun acceptsDotAndCommaDecimals() {
        assertThat(parseDecimal(" 4.5 ", range)).isEqualTo(ParsedNumber.Valid(4.5))
        assertThat(parseDecimal("4,5", range)).isEqualTo(ParsedNumber.Valid(4.5))
    }

    @Test
    fun reportsSpecificErrors() {
        assertThat(parseDecimal("  ", range)).isEqualTo(ParsedNumber.Invalid(InputError.EMPTY))
        assertThat(parseDecimal("4.5u", range)).isEqualTo(ParsedNumber.Invalid(InputError.NOT_A_NUMBER))
        assertThat(parseDecimal("NaN", range)).isEqualTo(ParsedNumber.Invalid(InputError.NOT_A_NUMBER))
        assertThat(parseDecimal("0", range)).isEqualTo(ParsedNumber.Invalid(InputError.OUT_OF_RANGE))
        assertThat(parseDecimal("150", range)).isEqualTo(ParsedNumber.Invalid(InputError.OUT_OF_RANGE))
    }
}
