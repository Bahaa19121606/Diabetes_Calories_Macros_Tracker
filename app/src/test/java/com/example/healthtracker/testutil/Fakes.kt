package com.example.healthtracker.testutil

import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.analysis.MacroAnalyzer
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.LabHbA1c
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.repository.CarbRatioTestRepository
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.SleepRepository
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.settings.ThemeMode
import com.example.healthtracker.domain.timer.CarbTestReminderScheduler
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import com.example.healthtracker.domain.timer.ScheduleResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Instant

class FakeMealRepository : MealRepository {
    val meals = MutableStateFlow<List<Meal>>(emptyList())
    var failWrites = false
    private var nextId = 1L

    override suspend fun add(meal: Meal): Long {
        if (failWrites) throw IOException("disk full")
        val id = nextId++
        meals.update { it + meal.copy(id = id) }
        return id
    }

    override suspend fun update(meal: Meal) = meals.update { list -> list.map { if (it.id == meal.id) meal else it } }
    override suspend fun delete(meal: Meal) = meals.update { list -> list.filterNot { it.id == meal.id } }
    override suspend fun get(id: Long) = meals.value.firstOrNull { it.id == id }
    override fun observeAll() = meals.map { list -> list.sortedByDescending { it.eatenAt } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Meal>> =
        meals.map { list -> list.filter { it.eatenAt >= from && it.eatenAt < to }.sortedByDescending { it.eatenAt } }

    override fun observeTotalsBetween(from: Instant, to: Instant): Flow<Macros> =
        observeBetween(from, to).map { list -> list.fold(Macros.ZERO) { acc, meal -> acc + meal.macros } }
}

class FakeInsulinRepository : InsulinRepository {
    val doses = MutableStateFlow<List<InsulinDose>>(emptyList())
    var failWrites = false
    private var nextId = 1L

    override suspend fun log(dose: InsulinDose): Long {
        if (failWrites) throw IOException("disk full")
        val id = nextId++
        doses.update { it + dose.copy(id = id) }
        return id
    }

    override suspend fun update(dose: InsulinDose) = doses.update { list -> list.map { if (it.id == dose.id) dose else it } }
    override suspend fun delete(dose: InsulinDose) = doses.update { list -> list.filterNot { it.id == dose.id } }
    override suspend fun get(id: Long) = doses.value.firstOrNull { it.id == id }
    override fun observeAll() = doses.map { list -> list.sortedByDescending { it.administeredAt } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<InsulinDose>> =
        doses.map { list ->
            list.filter { it.administeredAt >= from && it.administeredAt < to }.sortedByDescending { it.administeredAt }
        }

    override fun observeLatest(type: InsulinType): Flow<InsulinDose?> =
        doses.map { list -> list.filter { it.type == type }.maxByOrNull { it.administeredAt } }

    override fun observeTotalUnitsBetween(type: InsulinType, from: Instant, to: Instant): Flow<Double> =
        observeBetween(from, to).map { list -> list.filter { it.type == type }.sumOf { it.units } }
}

class FakeGlucoseRepository : GlucoseRepository {
    val readings = MutableStateFlow<List<GlucoseReading>>(emptyList())
    var failWrites = false
    private var nextId = 1L

    override suspend fun log(reading: GlucoseReading): Long {
        if (failWrites) throw IOException("disk full")
        val id = nextId++
        readings.update { it + reading.copy(id = id) }
        return id
    }

    override suspend fun update(reading: GlucoseReading) =
        readings.update { list -> list.map { if (it.id == reading.id) reading else it } }

    override suspend fun delete(reading: GlucoseReading) = readings.update { list -> list.filterNot { it.id == reading.id } }
    override suspend fun get(id: Long) = readings.value.firstOrNull { it.id == id }
    override fun observeAll() = readings.map { list -> list.sortedByDescending { it.measuredAt } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<GlucoseReading>> =
        readings.map { list -> list.filter { it.measuredAt >= from && it.measuredAt < to }.sortedByDescending { it.measuredAt } }

    override fun observeLatest(): Flow<GlucoseReading?> = readings.map { list -> list.maxByOrNull { it.measuredAt } }

    override suspend fun getLatest(context: GlucoseContext) =
        readings.value.filter { it.context == context }.maxByOrNull { it.measuredAt }
}

class FakePostMealTimerScheduler(var exactAllowed: Boolean = true) : PostMealTimerScheduler {
    private val _activeTimer = MutableStateFlow<PostMealTimer?>(null)
    override val activeTimer: StateFlow<PostMealTimer?> = _activeTimer.asStateFlow()
    var cancelCount = 0
        private set

    override fun schedule(timer: PostMealTimer): ScheduleResult {
        _activeTimer.value = timer
        return if (exactAllowed) ScheduleResult.EXACT else ScheduleResult.INEXACT_PERMISSION_MISSING
    }

    override fun cancel() {
        cancelCount++
        _activeTimer.value = null
    }

    override fun canScheduleExact() = exactAllowed
}

class FakeMacroAnalyzer(
    var result: MacroAnalysisResult = MacroAnalysisResult.Success(Macros(540, 32, 58, 18)),
    private val latencyMs: Long = 2_000,
) : MacroAnalyzer {
    val requests = mutableListOf<String>()

    override suspend fun analyze(description: String): MacroAnalysisResult {
        requests += description
        delay(latencyMs)
        return result
    }
}

class FakeSettingsRepository(initial: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT) : SettingsRepository {
    override val glucoseUnit = MutableStateFlow(initial)

    override suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        glucoseUnit.value = unit
    }

    override val dailyGoals = MutableStateFlow(SettingsRepository.DEFAULT_DAILY_GOALS)

    override suspend fun setDailyGoals(goals: Macros) {
        dailyGoals.value = goals
    }

    override val themeMode = MutableStateFlow(SettingsRepository.DEFAULT_THEME_MODE)

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override val labHbA1c = MutableStateFlow<LabHbA1c?>(null)

    override suspend fun setLabHbA1c(result: LabHbA1c?) {
        labHbA1c.value = result
    }
}

class FakeWorkoutRepository : WorkoutRepository {
    val workouts = MutableStateFlow<List<Workout>>(emptyList())
    private var nextId = 1L

    override suspend fun log(workout: Workout): Long {
        val id = nextId++
        workouts.update { it + workout.copy(id = id) }
        return id
    }

    override suspend fun delete(workout: Workout) = workouts.update { list -> list.filterNot { it.id == workout.id } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Workout>> =
        workouts.map { list -> list.filter { it.startedAt >= from && it.startedAt < to }.sortedByDescending { it.startedAt } }
}

class FakeCarbRatioTestRepository : CarbRatioTestRepository {
    val tests = MutableStateFlow<List<CarbRatioTest>>(emptyList())
    private var nextId = 1L

    override suspend fun start(test: CarbRatioTest): Long {
        val id = nextId++
        tests.update { it + test.copy(id = id) }
        return id
    }

    override suspend fun update(test: CarbRatioTest) = tests.update { list -> list.map { if (it.id == test.id) test else it } }
    override suspend fun get(id: Long) = tests.value.firstOrNull { it.id == id }

    override fun observeActive(): Flow<CarbRatioTest?> =
        tests.map { list -> list.filter { it.status == CarbRatioTestStatus.IN_PROGRESS }.maxByOrNull { it.startedAt } }

    override fun observeAll(): Flow<List<CarbRatioTest>> = tests.map { list -> list.sortedByDescending { it.startedAt } }
}

class FakeTherapyProfileRepository(initial: TherapyProfile = TherapyProfile()) : TherapyProfileRepository {
    override val profile = MutableStateFlow(initial)

    override suspend fun update(profile: TherapyProfile) {
        this.profile.value = profile
    }
}

class FakeCarbTestReminderScheduler(var exactAllowed: Boolean = true) : CarbTestReminderScheduler {
    var scheduled: Pair<Long, Instant>? = null
        private set
    var cancelCount = 0
        private set

    override fun schedule(testId: Long, startedAt: Instant): ScheduleResult {
        scheduled = testId to startedAt
        return if (exactAllowed) ScheduleResult.EXACT else ScheduleResult.INEXACT_PERMISSION_MISSING
    }

    override fun cancel() {
        cancelCount++
        scheduled = null
    }
}

class FakeCorrectionCheckScheduler(var exactAllowed: Boolean = true) : CorrectionCheckScheduler {
    override val activeCheck = MutableStateFlow<CorrectionCheck?>(null)
    var cancelCount = 0
        private set

    override fun schedule(check: CorrectionCheck): ScheduleResult {
        activeCheck.value = check
        return if (exactAllowed) ScheduleResult.EXACT else ScheduleResult.INEXACT_PERMISSION_MISSING
    }

    override fun cancel() {
        cancelCount++
        activeCheck.value = null
    }
}

class FakeSleepRepository : SleepRepository {
    val sessions = MutableStateFlow<List<SleepSession>>(emptyList())
    private var nextId = 1L

    override suspend fun log(session: SleepSession): Long {
        val id = nextId++
        sessions.update { it + session.copy(id = id) }
        return id
    }

    override suspend fun delete(session: SleepSession) = sessions.update { list -> list.filterNot { it.id == session.id } }

    override fun observeOverlapping(from: Instant, to: Instant): Flow<List<SleepSession>> =
        sessions.map { list -> list.filter { it.wakeTime.isAfter(from) && it.bedtime.isBefore(to) }.sortedByDescending { it.wakeTime } }
}
