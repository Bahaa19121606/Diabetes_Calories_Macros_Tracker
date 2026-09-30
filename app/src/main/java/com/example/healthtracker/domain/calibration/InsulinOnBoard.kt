package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import java.time.Duration
import java.time.Instant

/** NovoRapid still active from earlier doses, using a linear decay over its duration of action. */
object InsulinOnBoard {
    val DURATION: Duration = Duration.ofHours(4)

    fun remainingUnits(doses: List<InsulinDose>, now: Instant): Double =
        doses.filter { it.type == InsulinType.NOVORAPID && !it.administeredAt.isAfter(now) }
            .sumOf { dose ->
                val elapsed = Duration.between(dose.administeredAt, now)
                if (elapsed >= DURATION) 0.0 else dose.units * (1.0 - elapsed.toMillis().toDouble() / DURATION.toMillis())
            }

    fun lastNovorapidAt(doses: List<InsulinDose>, now: Instant): Instant? =
        doses.filter { it.type == InsulinType.NOVORAPID && !it.administeredAt.isAfter(now) }
            .maxOfOrNull { it.administeredAt }
}
