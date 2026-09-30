package com.example.healthtracker.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class InsulinDoseTest {

    @Test
    fun rejectsNonPositiveUnits() {
        for (units in listOf(0.0, -1.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                InsulinDose(type = InsulinType.NOVORAPID, units = units, administeredAt = Instant.EPOCH)
            }
        }
    }
}
