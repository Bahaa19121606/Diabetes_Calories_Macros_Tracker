package com.example.healthtracker.domain.review

import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.HbA1cUnits
import com.example.healthtracker.domain.model.LabHbA1c
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class HbA1cEstimate(
    /** Rounded to one decimal, like a lab report. */
    val percent: Double,
    val lab: LabHbA1c,
    /** How much of the estimate comes from logged readings rather than the lab result, 0–1. */
    val loggedShare: Double,
    /** Readings logged after the lab test within the last 30 days. */
    val recentReadings: Int,
    /** Whether readings before the lab test were used to correct for when you usually check. */
    val calibrated: Boolean,
    val confidence: Confidence,
) {
    enum class Confidence { LOW, MEDIUM, HIGH }

    val mmolPerMol: Int get() = HbA1cUnits.toMmolPerMol(percent)

    /** Change since the lab result, in percentage points. */
    val change: Double get() = ((percent - lab.percent) * 10).roundToInt() / 10.0
}

/**
 * Projects a lab HbA1c forward using the readings logged since.
 *
 * HbA1c reflects about 120 days of glucose (the life of a red blood cell), weighted toward
 * recent weeks: roughly half comes from the last 30 days, a quarter from the 30 before, and a
 * quarter from the 60 before that. Each day in that window counts as either
 * - the lab result's average glucose (ADAG: eAG = 28.7 × A1c − 46.7), for days up to the lab
 *   test or days after it with no readings (assumed unchanged), or
 * - that day's logged average, for days after the lab test.
 *
 * Finger-prick checks happen at particular times (before meals, at bedtime) and miss what
 * happens between them. When enough readings were logged before the lab test, their average
 * is compared with the lab result and the difference is applied to later readings.
 */
object HbA1cEstimator {
    const val LOOKBACK_DAYS = 120
    const val RECENT_DAYS = 30
    const val CALIBRATION_DAYS = 90L
    const val MIN_CALIBRATION_READINGS = 40
    private const val MIN_CALIBRATION_FACTOR = 0.8
    private const val MAX_CALIBRATION_FACTOR = 1.25

    private class Bucket(val fromDaysAgo: Int, val toDaysAgo: Int, val weight: Double)

    private val BUCKETS = listOf(Bucket(0, 30, 0.5), Bucket(30, 60, 0.25), Bucket(60, 120, 0.25))

    fun averageGlucoseFor(percent: Double): Double = 28.7 * percent - 46.7

    fun percentFor(averageMgDl: Double): Double = (averageMgDl + 46.7) / 28.7

    fun estimate(lab: LabHbA1c, readings: List<GlucoseReading>, today: LocalDate, zone: ZoneId): HbA1cEstimate {
        val labGlucose = averageGlucoseFor(lab.percent)
        val byDay = readings.groupBy { it.measuredAt.atZone(zone).toLocalDate() }
        val dailyAverage = byDay.mapValues { (_, day) -> day.map { it.mgDl }.average() }

        val calibrationFactor = calibrationFactor(lab, labGlucose, byDay, dailyAverage)

        var weighted = 0.0
        var loggedShare = 0.0
        for (bucket in BUCKETS) {
            val dayWeight = bucket.weight / (bucket.toDaysAgo - bucket.fromDaysAgo)
            for (ago in bucket.fromDaysAgo until bucket.toDaysAgo) {
                val date = today.minusDays(ago.toLong())
                val logged = dailyAverage[date]?.takeIf { date.isAfter(lab.measuredOn) }
                if (logged != null) {
                    weighted += dayWeight * logged * (calibrationFactor ?: 1.0)
                    loggedShare += dayWeight
                } else {
                    weighted += dayWeight * labGlucose
                }
            }
        }

        val recentFrom = maxOf(today.minusDays(RECENT_DAYS - 1L), lab.measuredOn.plusDays(1))
        val recentReadings = byDay.filterKeys { !it.isBefore(recentFrom) && !it.isAfter(today) }.values.sumOf { it.size }
        return HbA1cEstimate(
            percent = (percentFor(weighted) * 10).roundToInt() / 10.0,
            lab = lab,
            loggedShare = loggedShare,
            recentReadings = recentReadings,
            calibrated = calibrationFactor != null,
            confidence = when {
                loggedShare >= 0.75 && recentReadings >= 2 * RECENT_DAYS -> HbA1cEstimate.Confidence.HIGH
                loggedShare >= 0.4 && recentReadings >= RECENT_DAYS -> HbA1cEstimate.Confidence.MEDIUM
                else -> HbA1cEstimate.Confidence.LOW
            },
        )
    }

    /** Lab glucose ÷ logged glucose over the 90 days up to the lab test, when there's enough data. */
    private fun calibrationFactor(
        lab: LabHbA1c,
        labGlucose: Double,
        byDay: Map<LocalDate, List<GlucoseReading>>,
        dailyAverage: Map<LocalDate, Double>,
    ): Double? {
        val from = lab.measuredOn.minusDays(CALIBRATION_DAYS - 1)
        val days = byDay.keys.filter { !it.isBefore(from) && !it.isAfter(lab.measuredOn) }
        if (days.sumOf { byDay.getValue(it).size } < MIN_CALIBRATION_READINGS) return null
        val loggedGlucose = days.map { dailyAverage.getValue(it) }.average()
        return (labGlucose / loggedGlucose).coerceIn(MIN_CALIBRATION_FACTOR, MAX_CALIBRATION_FACTOR)
    }

    /** Days since the lab test; after about 3 months a new lab result is due. */
    fun daysSinceLab(lab: LabHbA1c, today: LocalDate): Long = ChronoUnit.DAYS.between(lab.measuredOn, today)

    const val RETEST_AFTER_DAYS = 90L
}
