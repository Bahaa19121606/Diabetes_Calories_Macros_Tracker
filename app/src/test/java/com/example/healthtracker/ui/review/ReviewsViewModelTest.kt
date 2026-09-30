package com.example.healthtracker.ui.review

import com.example.healthtracker.domain.model.GlucoseContext
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.LabHbA1c
import com.example.healthtracker.domain.review.PeriodRange
import com.example.healthtracker.domain.review.ReviewPeriod
import com.example.healthtracker.testutil.FakeGlucoseRepository
import com.example.healthtracker.testutil.FakeInsulinRepository
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.FakeSleepRepository
import com.example.healthtracker.testutil.FakeWorkoutRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // Wednesday.
    private val clock = MutableClock(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 9, 30)
    private val glucose = FakeGlucoseRepository()
    private val settings = FakeSettingsRepository()
    private val viewModel by lazy {
        ReviewsViewModel(FakeMealRepository(), FakeInsulinRepository(), glucose, FakeWorkoutRepository(), FakeSleepRepository(), settings, clock)
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun labValues_areRecognisedInPercentOrMmolPerMol() {
        assertThat(parseLabValue("7.1")).isEqualTo(LabValue.Valid(7.1))
        assertThat(parseLabValue("7,4")).isEqualTo(LabValue.Valid(7.4))
        // 54 mmol/mol = 7.09%.
        assertThat(parseLabValue("54")).isEqualTo(LabValue.Valid(7.1))
        assertThat(parseLabValue("17")).isEqualTo(LabValue.Invalid(LabError.OUT_OF_RANGE))
        assertThat(parseLabValue("3")).isEqualTo(LabValue.Invalid(LabError.OUT_OF_RANGE))
        assertThat(parseLabValue("abc")).isEqualTo(LabValue.Invalid(LabError.NOT_A_NUMBER))
        assertThat(parseLabValue(" ")).isEqualTo(LabValue.Invalid(LabError.EMPTY))
    }

    @Test
    fun startsOnThisWeek_andCantGoPastIt() = runTest {
        subscribe()
        val thisWeek = PeriodRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5))
        assertThat(viewModel.uiState.value.range).isEqualTo(thisWeek)
        assertThat(viewModel.uiState.value.isCurrentPeriod).isTrue()

        viewModel.next()
        assertThat(viewModel.uiState.value.range).isEqualTo(thisWeek)

        viewModel.previous()
        assertThat(viewModel.uiState.value.range.start).isEqualTo(LocalDate.of(2026, 9, 21))
        assertThat(viewModel.uiState.value.isCurrentPeriod).isFalse()

        viewModel.selectPeriod(ReviewPeriod.MONTH)
        assertThat(viewModel.uiState.value.range).isEqualTo(PeriodRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun summarizesTheSelectedPeriod_andThePreviousOne() = runTest {
        glucose.readings.value = listOf(
            GlucoseReading(id = 1, mgDl = 120, context = GlucoseContext.OTHER, measuredAt = Instant.parse("2026-09-29T08:00:00Z")),
            GlucoseReading(id = 2, mgDl = 200, context = GlucoseContext.OTHER, measuredAt = Instant.parse("2026-09-22T08:00:00Z")),
        )
        subscribe()

        assertThat(viewModel.uiState.value.current!!.glucose!!.averageMgDl).isEqualTo(120)
        assertThat(viewModel.uiState.value.previous!!.glucose!!.averageMgDl).isEqualTo(200)
    }

    @Test
    fun savingALabResult_storesIt_andShowsAnEstimate() = runTest {
        subscribe()
        assertThat(viewModel.uiState.value.estimate).isNull()

        viewModel.openLabEditor()
        viewModel.onLabValueChange("53")
        viewModel.onLabDateChange(today.minusDays(14))
        viewModel.saveLab()

        assertThat(settings.labHbA1c.value).isEqualTo(LabHbA1c(7.0, today.minusDays(14)))
        assertThat(viewModel.uiState.value.labForm).isNull()
        assertThat(viewModel.uiState.value.estimate!!.percent).isEqualTo(7.0)
    }

    @Test
    fun labResultsCantBeInTheFuture_orOutOfRange() = runTest {
        subscribe()
        viewModel.openLabEditor()
        viewModel.onLabValueChange("7.0")
        viewModel.onLabDateChange(today.plusDays(1))
        viewModel.saveLab()
        assertThat(viewModel.uiState.value.labForm!!.error).isEqualTo(LabError.FUTURE_DATE)

        viewModel.onLabDateChange(today)
        viewModel.onLabValueChange("19")
        viewModel.saveLab()
        assertThat(viewModel.uiState.value.labForm!!.error).isEqualTo(LabError.OUT_OF_RANGE)
        assertThat(settings.labHbA1c.value).isNull()
    }
}
