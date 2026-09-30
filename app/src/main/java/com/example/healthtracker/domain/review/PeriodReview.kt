package com.example.healthtracker.domain.review

import com.example.healthtracker.domain.calibration.GlucoseLimits
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.model.Workout
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Weeks run Monday to Sunday, so the weekly review lands on Sunday. */
enum class ReviewPeriod {
    WEEK,
    MONTH;

    fun rangeContaining(date: LocalDate): PeriodRange = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { PeriodRange(it, it.plusWeeks(1)) }
        MONTH -> date.withDayOfMonth(1).let { PeriodRange(it, it.plusMonths(1)) }
    }

    fun shift(range: PeriodRange, by: Long): PeriodRange = when (this) {
        WEEK -> rangeContaining(range.start.plusWeeks(by))
        MONTH -> rangeContaining(range.start.plusMonths(by))
    }
}

/** [start, endExclusive) in local dates. */
data class PeriodRange(val start: LocalDate, val endExclusive: LocalDate) {
    val lastDay: LocalDate get() = endExclusive.minusDays(1)
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    /** Days so far: the whole period once it's over, up to today while it's running. */
    fun daysElapsed(today: LocalDate): Int =
        ChronoUnit.DAYS.between(start, minOf(endExclusive, today.plusDays(1))).toInt().coerceAtLeast(0)
}

data class GlucoseSummary(
    val readings: Int,
    val averageMgDl: Int,
    /** Share of readings below 70, in 70–180, and above 180; they add up to 100. */
    val lowPercent: Int,
    val inRangePercent: Int,
    val highPercent: Int,
    val lows: Int,
    val lowestMgDl: Int,
    val highestMgDl: Int,
    val averageFastingMgDl: Int?,
)

data class DailyGlucose(val date: LocalDate, val averageMgDl: Int, val lowestMgDl: Int, val highestMgDl: Int)

data class NutritionSummary(val daysLogged: Int, val dailyAverage: Macros)

data class InsulinSummary(
    val novorapidDays: Int,
    val averageDailyNovorapid: Double?,
    val tresibaDoses: Int,
    val averageTresiba: Double?,
)

data class SleepSummary(val nights: Int, val average: Duration, val shortNights: Int)

data class ActivitySummary(val workouts: Int, val totalMinutes: Int)

data class PeriodSummary(
    val range: PeriodRange,
    val daysElapsed: Int,
    val glucose: GlucoseSummary?,
    val daily: List<DailyGlucose>,
    val nutrition: NutritionSummary?,
    val insulin: InsulinSummary?,
    val sleep: SleepSummary?,
    val activity: ActivitySummary,
) {
    val isEmpty: Boolean get() = glucose == null && nutrition == null && insulin == null && sleep == null && activity.workouts == 0
}

object PeriodReview {
    val SHORT_NIGHT: Duration = Duration.ofHours(6)

    fun summarize(
        range: PeriodRange,
        today: LocalDate,
        zone: ZoneId,
        readings: List<GlucoseReading>,
        meals: List<Meal>,
        doses: List<InsulinDose>,
        sleeps: List<SleepSession>,
        workouts: List<Workout>,
    ): PeriodSummary {
        fun java.time.Instant.date() = atZone(zone).toLocalDate()
        val periodReadings = readings.filter { it.measuredAt.date() in range }.sortedBy { it.measuredAt }
        val periodMeals = meals.filter { it.eatenAt.date() in range }
        val periodDoses = doses.filter { it.administeredAt.date() in range }
        // A night belongs to the day you woke up.
        val nights = sleeps.filter { it.wakeTime.date() in range }
        val periodWorkouts = workouts.filter { it.startedAt.date() in range }

        return PeriodSummary(
            range = range,
            daysElapsed = range.daysElapsed(today),
            glucose = glucoseSummary(periodReadings, zone),
            daily = periodReadings
                .groupBy { it.measuredAt.date() }
                .map { (date, day) ->
                    DailyGlucose(date, day.map { it.mgDl }.average().roundToInt(), day.minOf { it.mgDl }, day.maxOf { it.mgDl })
                }
                .sortedBy { it.date },
            nutrition = periodMeals.takeIf { it.isNotEmpty() }?.let { list ->
                val days = list.map { it.eatenAt.date() }.distinct().size
                val total = list.fold(Macros.ZERO) { acc, meal -> acc + meal.macros }
                NutritionSummary(
                    daysLogged = days,
                    dailyAverage = Macros(
                        calories = total.calories / days,
                        proteinGrams = total.proteinGrams / days,
                        carbsGrams = total.carbsGrams / days,
                        fatGrams = total.fatGrams / days,
                    ),
                )
            },
            insulin = insulinSummary(periodDoses, zone),
            sleep = nights.takeIf { it.isNotEmpty() }?.let { list ->
                SleepSummary(
                    nights = list.size,
                    average = Duration.ofSeconds(list.sumOf { it.duration.seconds } / list.size),
                    shortNights = list.count { it.duration < SHORT_NIGHT },
                )
            },
            activity = ActivitySummary(periodWorkouts.size, periodWorkouts.sumOf { it.durationMinutes }),
        )
    }

    private fun glucoseSummary(readings: List<GlucoseReading>, zone: ZoneId): GlucoseSummary? {
        if (readings.isEmpty()) return null
        val values = readings.map { it.mgDl }
        val lows = values.count { it < GlucoseLimits.LOW_MG_DL }
        val highs = values.count { it > GlucoseLimits.HIGH_MG_DL }
        val lowPercent = percent(lows, values.size)
        val highPercent = percent(highs, values.size)
        // The first fasting reading of each morning.
        val fasting = readings
            .filter { it.context == GlucoseContext.FASTING }
            .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
            .map { (_, day) -> day.first().mgDl }
        return GlucoseSummary(
            readings = values.size,
            averageMgDl = values.average().roundToInt(),
            lowPercent = lowPercent,
            inRangePercent = (100 - lowPercent - highPercent).coerceAtLeast(0),
            highPercent = highPercent,
            lows = lows,
            lowestMgDl = values.min(),
            highestMgDl = values.max(),
            averageFastingMgDl = fasting.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }

    private fun insulinSummary(doses: List<InsulinDose>, zone: ZoneId): InsulinSummary? {
        if (doses.isEmpty()) return null
        val rapid = doses.filter { it.type == InsulinType.NOVORAPID }
        val basal = doses.filter { it.type == InsulinType.TRESIBA }
        val rapidDays = rapid.map { it.administeredAt.atZone(zone).toLocalDate() }.distinct().size
        return InsulinSummary(
            novorapidDays = rapidDays,
            averageDailyNovorapid = if (rapidDays > 0) rapid.sumOf { it.units } / rapidDays else null,
            tresibaDoses = basal.size,
            averageTresiba = basal.takeIf { it.isNotEmpty() }?.map { it.units }?.average(),
        )
    }

    private fun percent(part: Int, whole: Int): Int = (part * 100.0 / whole).roundToInt()
}
