package com.example.healthtracker.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.calibration.WeeklyReview
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.domain.model.Meal
import com.example.healthtracker.domain.model.SleepSession
import com.example.healthtracker.domain.model.Workout
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.SleepRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.domain.timer.CorrectionCheck
import com.example.healthtracker.domain.timer.CorrectionCheckScheduler
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.PostMealTimerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface TimelineEntry {
    /** Unique across entry types, for LazyColumn keys. */
    val key: String
    val at: Instant

    data class MealEntry(val meal: Meal) : TimelineEntry {
        override val key get() = "meal-${meal.id}"
        override val at get() = meal.eatenAt
    }

    data class InsulinEntry(val dose: InsulinDose) : TimelineEntry {
        override val key get() = "insulin-${dose.id}"
        override val at get() = dose.administeredAt
    }

    data class GlucoseEntry(val reading: GlucoseReading) : TimelineEntry {
        override val key get() = "glucose-${reading.id}"
        override val at get() = reading.measuredAt
    }

    data class WorkoutEntry(val workout: Workout) : TimelineEntry {
        override val key get() = "workout-${workout.id}"
        override val at get() = workout.startedAt
    }

    /** A nap, or any sleep that isn't one of the day's bookends. */
    data class SleepEntry(val session: SleepSession) : TimelineEntry {
        override val key get() = "sleep-${session.id}"
        override val at get() = session.bedtime
    }
}

data class DaySummary(
    val macros: Macros = Macros.ZERO,
    val novorapidUnits: Double = 0.0,
    val tresibaUnits: Double = 0.0,
    val readingCount: Int = 0,
    val averageGlucoseMgDl: Int? = null,
)

data class TimelineUiState(
    val date: LocalDate,
    val isToday: Boolean,
    /** Oldest first: the day reads top to bottom, from waking up to going to bed. */
    val entries: List<TimelineEntry> = emptyList(),
    val summary: DaySummary = DaySummary(),
    val goals: Macros = SettingsRepository.DEFAULT_DAILY_GOALS,
    /** The night that ended this morning: the top bookend. */
    val wake: SleepSession? = null,
    /** Going to bed this evening: the bottom bookend. */
    val bedtime: SleepSession? = null,
    val latestGlucose: GlucoseReading? = null,
    val activeTimer: PostMealTimer? = null,
    val correctionCheck: CorrectionCheck? = null,
    /** Sunday: time for the weekly Tresiba review. */
    val showWeeklyReview: Boolean = false,
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val isLoading: Boolean = true,
)

private data class DayData(
    val meals: List<Meal>,
    val doses: List<InsulinDose>,
    val readings: List<GlucoseReading>,
    val workouts: List<Workout>,
    val sleeps: List<SleepSession>,
)

private data class Reminders(val timer: PostMealTimer?, val check: CorrectionCheck?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val insulinRepository: InsulinRepository,
    private val glucoseRepository: GlucoseRepository,
    private val workoutRepository: WorkoutRepository,
    private val sleepRepository: SleepRepository,
    settingsRepository: SettingsRepository,
    private val timerScheduler: PostMealTimerScheduler,
    private val correctionCheckScheduler: CorrectionCheckScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(today())

    private val dayState = selectedDate.flatMapLatest { date ->
        val from = date.atStartOfDay(clock.zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(clock.zone).toInstant()
        combine(
            mealRepository.observeBetween(from, to),
            insulinRepository.observeBetween(from, to),
            glucoseRepository.observeBetween(from, to),
            workoutRepository.observeBetween(from, to),
            sleepRepository.observeOverlapping(from, to),
        ) { meals, doses, readings, workouts, sleeps ->
            buildState(date, from, to, DayData(meals, doses, readings, workouts, sleeps))
        }
    }

    private val reminders = combine(timerScheduler.activeTimer, correctionCheckScheduler.activeCheck, ::Reminders)

    val uiState: StateFlow<TimelineUiState> = combine(
        dayState,
        settingsRepository.glucoseUnit,
        settingsRepository.dailyGoals,
        glucoseRepository.observeLatest(),
        reminders,
    ) { state, unit, goals, latest, (timer, check) ->
        state.copy(
            glucoseUnit = unit,
            goals = goals,
            latestGlucose = latest.takeIf { state.isToday },
            activeTimer = timer.takeIf { state.isToday },
            correctionCheck = check.takeIf { state.isToday },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimelineUiState(date = selectedDate.value, isToday = true),
    )

    fun previousDay() = selectedDate.update { it.minusDays(1) }

    fun nextDay() = selectedDate.update { if (it < today()) it.plusDays(1) else it }

    fun goToToday() {
        selectedDate.value = today()
    }

    fun delete(entry: TimelineEntry) {
        viewModelScope.launch {
            when (entry) {
                is TimelineEntry.MealEntry -> mealRepository.delete(entry.meal)
                is TimelineEntry.InsulinEntry -> insulinRepository.delete(entry.dose)
                is TimelineEntry.GlucoseEntry -> glucoseRepository.delete(entry.reading)
                is TimelineEntry.WorkoutEntry -> workoutRepository.delete(entry.workout)
                is TimelineEntry.SleepEntry -> sleepRepository.delete(entry.session)
            }
        }
    }

    fun cancelTimer() = timerScheduler.cancel()

    fun cancelCorrectionCheck() = correctionCheckScheduler.cancel()

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun buildState(date: LocalDate, from: Instant, to: Instant, data: DayData): TimelineUiState {
        fun Instant.onThisDay() = !isBefore(from) && isBefore(to)
        val wake = data.sleeps.filter { it.wakeTime.onThisDay() }.minByOrNull { it.wakeTime }
        val bedtime = data.sleeps.filter { it != wake && it.bedtime.onThisDay() }.maxByOrNull { it.bedtime }
        val otherSleep = data.sleeps.filter { it != wake && it != bedtime }

        val entries = buildList<TimelineEntry> {
            data.meals.mapTo(this) { TimelineEntry.MealEntry(it) }
            data.doses.mapTo(this) { TimelineEntry.InsulinEntry(it) }
            data.readings.mapTo(this) { TimelineEntry.GlucoseEntry(it) }
            data.workouts.mapTo(this) { TimelineEntry.WorkoutEntry(it) }
            otherSleep.mapTo(this) { TimelineEntry.SleepEntry(it) }
        }.sortedBy { it.at }

        val summary = DaySummary(
            macros = data.meals.fold(Macros.ZERO) { acc, meal -> acc + meal.macros },
            novorapidUnits = data.doses.filter { it.type == InsulinType.NOVORAPID }.sumOf { it.units },
            tresibaUnits = data.doses.filter { it.type == InsulinType.TRESIBA }.sumOf { it.units },
            readingCount = data.readings.size,
            averageGlucoseMgDl = data.readings.takeIf { it.isNotEmpty() }?.map { it.mgDl }?.average()?.roundToInt(),
        )
        val isToday = date == today()
        return TimelineUiState(
            date = date,
            isToday = isToday,
            entries = entries,
            summary = summary,
            wake = wake,
            bedtime = bedtime,
            showWeeklyReview = isToday && WeeklyReview.isReviewDay(date),
            isLoading = false,
        )
    }
}
