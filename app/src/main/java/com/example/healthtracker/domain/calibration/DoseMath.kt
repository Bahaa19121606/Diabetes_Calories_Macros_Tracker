package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DoseMath {
    /** The NovoRapid pen doses in whole units: rounds to the nearest unit, halves up (4.5 → 5). */
    fun roundToWholeUnit(units: Double): Double = floor(units + 0.5 + 1e-9)

    /** 500 rule: theoretical grams covered by one unit. */
    fun rule500CarbRatio(totalDailyDose: Double): Double = 500.0 / totalDailyDose

    /** 1800 rule (rapid-acting analogues): theoretical mg/dL drop per unit. */
    fun rule1800CorrectionFactor(totalDailyDose: Double): Double = 1800.0 / totalDailyDose

    /** A ratio about 10% stronger (fewer grams per unit = more insulin), always at least 1 g. */
    fun strongerRatio(ratio: Double): Double = max(3.0, min((ratio * 0.9).roundToInt().toDouble(), ratio - 1.0))

    /** A ratio about 10% weaker (more grams per unit = less insulin), always at least 1 g. */
    fun weakerRatio(ratio: Double): Double = max((ratio * 1.1).roundToInt().toDouble(), ratio + 1.0)

    data class DailyDoseStats(
        val days: Int,
        val totalDailyDose: Double,
        val basalDailyDose: Double,
    ) {
        /** The 500/1800 rules assume basal is about half of the total. */
        val basalShare: Double get() = if (totalDailyDose > 0) basalDailyDose / totalDailyDose else 0.0
    }

    /**
     * Average daily insulin over the days in the last [window] that have at least one dose logged.
     * Null with fewer than [minDays] such days: a single day says little about the average.
     */
    fun dailyDoseStats(
        doses: List<InsulinDose>,
        now: Instant,
        zone: ZoneId,
        window: Duration = Duration.ofDays(7),
        minDays: Int = 3,
    ): DailyDoseStats? {
        val inWindow = doses.filter { !it.administeredAt.isAfter(now) && it.administeredAt.isAfter(now.minus(window)) }
        val days = inWindow.map { it.administeredAt.atZone(zone).toLocalDate() }.distinct().size
        if (days < minDays) return null
        return DailyDoseStats(
            days = days,
            totalDailyDose = inWindow.sumOf { it.units } / days,
            basalDailyDose = inWindow.filter { it.type == InsulinType.TRESIBA }.sumOf { it.units } / days,
        )
    }
}
