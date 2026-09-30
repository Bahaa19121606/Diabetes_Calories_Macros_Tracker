package com.example.healthtracker.data.mapper

import com.example.healthtracker.data.local.entity.CarbRatioTestEntity
import com.example.healthtracker.data.local.entity.GlucoseReadingEntity
import com.example.healthtracker.data.local.entity.InsulinDoseEntity
import com.example.healthtracker.data.local.entity.MealEntity
import com.example.healthtracker.data.local.entity.SleepSessionEntity
import com.example.healthtracker.data.local.entity.WorkoutEntity
import com.example.healthtracker.data.local.model.MacroTotalsRow
import com.example.healthtracker.domain.model.BolusBreakdown
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.model.Workout
import java.time.Instant

fun MealEntity.toDomain() = Meal(
    id = id,
    description = description,
    macros = Macros(calories, proteinGrams, carbsGrams, fatGrams),
    eatenAt = Instant.ofEpochMilli(eatenAt),
)

fun Meal.toEntity() = MealEntity(
    id = id,
    description = description,
    calories = macros.calories,
    proteinGrams = macros.proteinGrams,
    carbsGrams = macros.carbsGrams,
    fatGrams = macros.fatGrams,
    eatenAt = eatenAt.toEpochMilli(),
)

fun MacroTotalsRow.toDomain() = Macros(calories, proteinGrams, carbsGrams, fatGrams)

fun InsulinDoseEntity.toDomain() = InsulinDose(
    id = id,
    type = type,
    units = units,
    administeredAt = Instant.ofEpochMilli(administeredAt),
    mealId = mealId,
    note = note,
    bolus = if (carbsGrams != null && carbRatio != null) {
        BolusBreakdown(carbsGrams, carbRatio, correctionUnits ?: 0.0, correctionFactor)
    } else {
        null
    },
)

fun InsulinDose.toEntity() = InsulinDoseEntity(
    id = id,
    type = type,
    units = units,
    administeredAt = administeredAt.toEpochMilli(),
    mealId = mealId,
    note = note,
    carbsGrams = bolus?.carbsGrams,
    carbRatio = bolus?.carbRatio,
    correctionUnits = bolus?.correctionUnits,
    correctionFactor = bolus?.correctionFactor,
)

fun GlucoseReadingEntity.toDomain() = GlucoseReading(
    id = id,
    mgDl = mgDl,
    context = context,
    measuredAt = Instant.ofEpochMilli(measuredAt),
    mealId = mealId,
    note = note,
)

fun GlucoseReading.toEntity() = GlucoseReadingEntity(
    id = id,
    mgDl = mgDl,
    context = context,
    measuredAt = measuredAt.toEpochMilli(),
    mealId = mealId,
    note = note,
)

fun WorkoutEntity.toDomain() = Workout(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    durationMinutes = durationMinutes,
    intensity = intensity,
    note = note,
)

fun Workout.toEntity() = WorkoutEntity(
    id = id,
    startedAt = startedAt.toEpochMilli(),
    durationMinutes = durationMinutes,
    intensity = intensity,
    note = note,
)

fun CarbRatioTestEntity.toDomain() = CarbRatioTest(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    startMgDl = startMgDl,
    carbsGrams = carbsGrams,
    ratioTested = ratioTested,
    doseUnits = doseUnits,
    mgDlAt2h = mgDlAt2h,
    mgDlAt3h = mgDlAt3h,
    mgDlAt4h = mgDlAt4h,
    status = status,
    endedAt = endedAt?.let(Instant::ofEpochMilli),
)

fun CarbRatioTest.toEntity() = CarbRatioTestEntity(
    id = id,
    startedAt = startedAt.toEpochMilli(),
    startMgDl = startMgDl,
    carbsGrams = carbsGrams,
    ratioTested = ratioTested,
    doseUnits = doseUnits,
    mgDlAt2h = mgDlAt2h,
    mgDlAt3h = mgDlAt3h,
    mgDlAt4h = mgDlAt4h,
    status = status,
    endedAt = endedAt?.toEpochMilli(),
)

fun SleepSessionEntity.toDomain() = SleepSession(
    id = id,
    bedtime = Instant.ofEpochMilli(bedtime),
    wakeTime = Instant.ofEpochMilli(wakeTime),
)

fun SleepSession.toEntity() = SleepSessionEntity(
    id = id,
    bedtime = bedtime.toEpochMilli(),
    wakeTime = wakeTime.toEpochMilli(),
)
