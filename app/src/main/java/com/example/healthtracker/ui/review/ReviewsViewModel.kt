package com.example.healthtracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.LabHbA1c
import com.example.healthtracker.domain.repository.GlucoseRepository
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.MealRepository
import com.example.healthtracker.domain.repository.SleepRepository
import com.example.healthtracker.domain.repository.WorkoutRepository
import com.example.healthtracker.domain.review.HbA1cEstimate
import com.example.healthtracker.domain.review.HbA1cEstimator
import com.example.healthtracker.domain.review.PeriodRange
import com.example.healthtracker.domain.review.PeriodReview
import com.example.healthtracker.domain.review.PeriodSummary
import com.example.healthtracker.domain.review.ReviewPeriod
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.parseDecimal
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
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class LabError { EMPTY, NOT_A_NUMBER, OUT_OF_RANGE, FUTURE_DATE }

/** The lab result being entered; the value may be in % or mmol/mol. */
data class LabForm(val value: String, val date: LocalDate, val error: LabError? = null)

data class ReviewsUiState(
    val period: ReviewPeriod = ReviewPeriod.WEEK,
    val range: PeriodRange,
    val today: LocalDate,
    val current: PeriodSummary? = null,
    /** The period before, for "vs last week" comparisons. */
    val previous: PeriodSummary? = null,
    val lab: LabHbA1c? = null,
    val estimate: HbA1cEstimate? = null,
    val labForm: LabForm? = null,
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val isLoading: Boolean = true,
) {
    val isCurrentPeriod: Boolean get() = today in range
}

private data class Selection(val period: ReviewPeriod, val range: PeriodRange)

private data class Loaded(
    val current: PeriodSummary,
    val previous: PeriodSummary,
    val lab: LabHbA1c?,
    val estimate: HbA1cEstimate?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val insulinRepository: InsulinRepository,
    private val glucoseRepository: GlucoseRepository,
    private val workoutRepository: WorkoutRepository,
    private val sleepRepository: SleepRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection(ReviewPeriod.WEEK, ReviewPeriod.WEEK.rangeContaining(today())))
    private val labForm = MutableStateFlow<LabForm?>(null)

    private val loaded = combine(selection, settingsRepository.labHbA1c, ::Pair).flatMapLatest { (sel, lab) ->
        val today = today()
        val previousRange = sel.period.shift(sel.range, -1)
        // Enough history for both periods and for the HbA1c estimate.
        val fromDate = listOfNotNull(
            previousRange.start,
            today.minusDays(HbA1cEstimator.LOOKBACK_DAYS - 1L),
            lab?.measuredOn?.minusDays(HbA1cEstimator.CALIBRATION_DAYS - 1),
        ).min()
        val toDate = maxOf(sel.range.endExclusive, today.plusDays(1))
        val from = fromDate.atStartOfDay(clock.zone).toInstant()
        val to = toDate.atStartOfDay(clock.zone).toInstant()
        combine(
            glucoseRepository.observeBetween(from, to),
            mealRepository.observeBetween(from, to),
            insulinRepository.observeBetween(from, to),
            sleepRepository.observeOverlapping(from, to),
            workoutRepository.observeBetween(from, to),
        ) { readings, meals, doses, sleeps, workouts ->
            fun summary(range: PeriodRange) =
                PeriodReview.summarize(range, today, clock.zone, readings, meals, doses, sleeps, workouts)
            Loaded(
                current = summary(sel.range),
                previous = summary(previousRange),
                lab = lab,
                estimate = lab?.let { HbA1cEstimator.estimate(it, readings, today, clock.zone) },
            )
        }
    }

    val uiState: StateFlow<ReviewsUiState> = combine(
        selection,
        loaded,
        labForm,
        settingsRepository.glucoseUnit,
    ) { sel, data, form, unit ->
        ReviewsUiState(
            period = sel.period,
            range = sel.range,
            today = today(),
            current = data.current.takeIf { it.range == sel.range },
            previous = data.previous,
            lab = data.lab,
            estimate = data.estimate,
            labForm = form,
            glucoseUnit = unit,
            isLoading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReviewsUiState(range = selection.value.range, today = today()),
    )

    fun selectPeriod(period: ReviewPeriod) {
        selection.update { if (it.period == period) it else Selection(period, period.rangeContaining(today())) }
    }

    fun previous() = selection.update { it.copy(range = it.period.shift(it.range, -1)) }

    fun next() = selection.update { sel ->
        if (today() in sel.range) sel else sel.copy(range = sel.period.shift(sel.range, 1))
    }

    fun goToCurrent() = selection.update { it.copy(range = it.period.rangeContaining(today())) }

    fun openLabEditor() {
        val lab = uiState.value.lab
        labForm.value = LabForm(value = lab?.percent?.toString().orEmpty(), date = lab?.measuredOn ?: today())
    }

    fun onLabValueChange(value: String) = labForm.update { it?.copy(value = value, error = null) }

    fun onLabDateChange(date: LocalDate) = labForm.update { it?.copy(date = date, error = null) }

    fun dismissLabEditor() {
        labForm.value = null
    }

    fun saveLab() {
        val form = labForm.value ?: return
        if (form.date.isAfter(today())) {
            labForm.value = form.copy(error = LabError.FUTURE_DATE)
            return
        }
        when (val parsed = parseLabValue(form.value)) {
            is LabValue.Invalid -> labForm.value = form.copy(error = parsed.error)
            is LabValue.Valid -> {
                labForm.value = null
                viewModelScope.launch { settingsRepository.setLabHbA1c(LabHbA1c(parsed.percent, form.date)) }
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}

sealed interface LabValue {
    data class Valid(val percent: Double) : LabValue
    data class Invalid(val error: LabError) : LabValue
}

/**
 * Lab reports use either % (DCCT, e.g. 7.1) or mmol/mol (IFCC, e.g. 54). The ranges don't
 * overlap, so the unit is recognised from the number itself.
 */
fun parseLabValue(text: String): LabValue {
    val value = when (val parsed = parseDecimal(text, 0.0..Double.MAX_VALUE)) {
        is ParsedNumber.Valid -> parsed.value
        is ParsedNumber.Invalid -> return LabValue.Invalid(
            when (parsed.error) {
                InputError.EMPTY -> LabError.EMPTY
                InputError.NOT_A_NUMBER -> LabError.NOT_A_NUMBER
                InputError.OUT_OF_RANGE -> LabError.OUT_OF_RANGE
            },
        )
    }
    val percent = when (value) {
        in LabHbA1c.MIN_PERCENT..LabHbA1c.MAX_PERCENT -> value
        in MIN_MMOL_PER_MOL..MAX_MMOL_PER_MOL -> value / 10.929 + 2.15
        else -> return LabValue.Invalid(LabError.OUT_OF_RANGE)
    }
    return LabValue.Valid((percent * 10).roundToInt() / 10.0)
}

private const val MIN_MMOL_PER_MOL = 20.0
private const val MAX_MMOL_PER_MOL = 140.0
