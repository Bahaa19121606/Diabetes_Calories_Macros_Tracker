package com.example.healthtracker.ui.common

import com.example.healthtracker.domain.model.GlucoseUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class GlucoseInputTest {

    @Test
    fun convertsBetweenUnits() {
        assertThat(convertGlucoseInput("101", GlucoseUnit.MG_DL, GlucoseUnit.MMOL_L)).isEqualTo("5.6")
        assertThat(convertGlucoseInput("5,6", GlucoseUnit.MMOL_L, GlucoseUnit.MG_DL)).isEqualTo("101")
    }

    @Test
    fun leavesEmptyOrInvalidTextAlone() {
        assertThat(convertGlucoseInput("", GlucoseUnit.MG_DL, GlucoseUnit.MMOL_L)).isEmpty()
        assertThat(convertGlucoseInput("abc", GlucoseUnit.MG_DL, GlucoseUnit.MMOL_L)).isEqualTo("abc")
        assertThat(convertGlucoseInput("5.6", GlucoseUnit.MMOL_L, GlucoseUnit.MMOL_L)).isEqualTo("5.6")
    }

    @Test
    fun resolveOccurredAt_nullIsNow_futureIsRejected() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        assertThat(resolveOccurredAt(null, now)).isEqualTo(now)
        assertThat(resolveOccurredAt(now.minusSeconds(60), now)).isEqualTo(now.minusSeconds(60))
        assertThat(resolveOccurredAt(now.plusSeconds(60), now)).isNull()
    }
}
