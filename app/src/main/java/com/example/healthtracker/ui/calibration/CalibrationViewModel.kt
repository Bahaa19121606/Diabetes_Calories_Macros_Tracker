package com.example.healthtracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.calibration.CarbRatioTestProtocol
import com.example.healthtracker.domain.calibration.CorrectionFactorAnalyzer
import com.example.healthtracker.domain.calibration.CorrectionFactorReport
import com.example.healthtracker.domain.calibration.MealResponseAnalyzer
import com.example.healthtracker.domain.calibration.MealResponseReport
import com.example.healthtracker.domain.calibration.TestGrade
import com.example.healthtracker.domain.calibration.TresibaAnalyzer
import com.example.healthtracker.domain.calibration.TresibaReport
import com.example.healthtracker.domain.calibration.WeeklyContext
import com.example.healthtracker.domain.calibration.WeeklyReview
import com.example.healthtracker.domain.model.CarbRatioTest
import com.example.healthtracker.domain.model.CarbRatioTestStatus
import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinDose
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/** The four checks of the daily review protocol, in order. */
val DAILY_CHECKS = listOf(GlucoseContext.FASTING, GlucoseContext.PRE_MEAL, GlucoseContext.POST_MEAL, GlucoseContext.BEDTIME)

data class DayCoverage(val date: LocalDate, val done: Set<GlucoseContext>)

data class GradedTest(val test: CarbRatioTest, val grade: TestGrade)

data class CalibrationUiState(
    val isLoading: Boolean = true,
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val profile: TherapyProfile = TherapyProfile(),
    /** Today first. */
    val coverage: List<DayCoverage> = emptyList(),
    val tresiba: TresibaReport? = null,
    /** Fasting glucose next to sleep for the last 7 days. */
    val weekly: WeeklyContext? = null,
    val meals: MealResponseReport? = null,
    val corrections: CorrectionFactorReport? = null,
    val activeTest: CarbRatioTest? = null,
    /** Finished tests, newest first. */
    val testHistory: List<GradedTest> = emptyList(),
    val ratioApplied: Double? = null,
    /** mg/dL per unit. */
    val factorApplied: Double? = null,
)

private data class Log(
    val readings: List<GlucoseReading>,
    val doses: List<InsulinDose>,
    val meals: List<Meal>,
    val workouts: List<Workout>,
    val sleeps: List<SleepSession>,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    glucoseRepository: GlucoseRepository,
    insulinRepository: InsulinRepository,
    mealRepository: MealRepository,
    workoutRepository: WorkoutRepository,
    sleepRepository: SleepRepository,
    carbRatioTestRepository: CarbRatioTestRepository,
    private val profileRepository: TherapyProfileRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val ratioApplied = MutableStateFlow<Double?>(null)
    private val factorApplied = MutableStateFlow<Double?>(null)
    private val applied = combine(ratioApplied, factorApplied) { ratio, factor -> ratio to factor }

    // Tresiba needs its full dose history to find the last change; everything else looks back 14 days.
    private val from = clock.instant().minus(Duration.ofDays(ANALYSIS_DAYS))
    private val to = clock.instant().plus(Duration.ofDays(1))

    private val log = combine(
        glucoseRepository.observeBetween(from, to),
        insulinRepository.observeAll(),
        mealRepository.observeBetween(from, to),
        workoutRepository.observeBetween(from, to),
        sleepRepository.observeOverlapping(from, to),
    ) { readings, doses, meals, workouts, sleeps -> Log(readings, doses, meals, workouts, sleeps) }

    val uiState: StateFlow<CalibrationUiState> = combine(
        log,
        profileRepository.profile,
        settingsRepository.glucoseUnit,
        carbRatioTestRepository.observeAll(),
        applied,
    ) { log, profile, unit, tests, (ratio, factor) ->
        val now = clock.instant()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()
        val recentDoses = log.doses.filter { it.administeredAt.isAfter(from) }
        CalibrationUiState(
            isLoading = false,
            glucoseUnit = unit,
            profile = profile,
            coverage = (0L until COVERAGE_DAYS).map { back ->
                val date = today.minusDays(back)
                DayCoverage(
                    date = date,
                    done = log.readings.filter { it.measuredAt.atZone(zone).toLocalDate() == date }.map { it.context }.toSet(),
                )
            },
            tresiba = TresibaAnalyzer.analyze(log.readings, log.doses, log.meals, log.workouts, now, zone),
            weekly = WeeklyReview.context(log.readings, log.sleeps, now, zone),
            meals = MealResponseAnalyzer.analyze(profile.carbRatio, recentDoses, log.meals, log.readings, log.workouts, now),
            corrections = CorrectionFactorAnalyzer.analyze(profile.correctionFactor, recentDoses, log.meals, log.readings, log.workouts, now),
            activeTest = tests.firstOrNull { it.status == CarbRatioTestStatus.IN_PROGRESS },
            testHistory = tests
                .filter { it.status != CarbRatioTestStatus.IN_PROGRESS }
                .map { GradedTest(it, CarbRatioTestProtocol.grade(it)) },
            ratioApplied = ratio,
            factorApplied = factor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationUiState())

    /** Adopts a suggested carb ratio. Starts a new 72-hour monitoring period for meals. */
    fun applyCarbRatio(ratio: Double) {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            profileRepository.update(profile.copy(carbRatio = ratio))
            ratioApplied.value = ratio
        }
    }

    fun ratioAppliedShown() {
        ratioApplied.value = null
    }

    /** Adopts a suggested correction factor (mg/dL per unit). Starts a new 72-hour monitoring period. */
    fun applyCorrectionFactor(factor: Double) {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            profileRepository.update(profile.copy(correctionFactor = factor))
            factorApplied.value = factor
        }
    }

    fun factorAppliedShown() {
        factorApplied.value = null
    }

    private companion object {
        const val ANALYSIS_DAYS = 14L
        const val COVERAGE_DAYS = 3L
    }
}
