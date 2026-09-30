package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.BolusBreakdown
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.model.WorkoutIntensity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/** Small builders so each test reads as a timeline. All times are UTC. */
object CalibrationFixtures {
    val ZONE: ZoneOffset = ZoneOffset.UTC
    private val ids = AtomicLong(1)

    fun at(date: LocalDate, time: String): Instant = date.atTime(LocalTime.parse(time)).toInstant(ZONE)

    fun reading(mgDl: Int, at: Instant, context: GlucoseContext = GlucoseContext.OTHER) =
        GlucoseReading(id = ids.getAndIncrement(), mgDl = mgDl, context = context, measuredAt = at)

    fun novorapid(
        units: Double,
        at: Instant,
        carbs: Int? = null,
        ratio: Double = 12.0,
        correction: Double = 0.0,
        mealId: Long? = null,
        factor: Double? = null,
    ) =
        InsulinDose(
            id = ids.getAndIncrement(),
            type = InsulinType.NOVORAPID,
            units = units,
            administeredAt = at,
            mealId = mealId,
            bolus = carbs?.let { BolusBreakdown(it, ratio, correction, factor) },
        )

    fun tresiba(units: Double, at: Instant) =
        InsulinDose(id = ids.getAndIncrement(), type = InsulinType.TRESIBA, units = units, administeredAt = at)

    fun meal(at: Instant, carbs: Int = 60, protein: Int = 25, fat: Int = 15) =
        Meal(id = ids.getAndIncrement(), description = "meal", macros = Macros(500, protein, carbs, fat), eatenAt = at)

    fun workout(start: Instant, minutes: Int = 60, intensity: WorkoutIntensity = WorkoutIntensity.HEAVY_LIFTING) =
        Workout(id = ids.getAndIncrement(), startedAt = start, durationMinutes = minutes, intensity = intensity)
}
