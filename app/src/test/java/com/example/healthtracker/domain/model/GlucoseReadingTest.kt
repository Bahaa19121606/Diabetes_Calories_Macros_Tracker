package com.example.healthtracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.math.roundToInt

class GlucoseReadingTest {

    @Test
    fun mmolToMgDl_roundTripsEveryOneDecimalValue() {
        // 1.2 .. 33.3 mmol/L covers the whole 20..600 mg/dL meter range.
        for (tenths in 12..333) {
            val mmol = tenths / 10.0
            val reading = GlucoseReading.fromMmolL(mmol, GlucoseContext.OTHER, Instant.EPOCH)
            assertThat((reading.mmolL * 10).roundToInt()).isEqualTo(tenths)
        }
    }

    @Test
    fun rejectsImplausibleValues() {
        assertThrows(IllegalArgumentException::class.java) {
            GlucoseReading(mgDl = 5, context = GlucoseContext.OTHER, measuredAt = Instant.EPOCH)
        }
    }
}
