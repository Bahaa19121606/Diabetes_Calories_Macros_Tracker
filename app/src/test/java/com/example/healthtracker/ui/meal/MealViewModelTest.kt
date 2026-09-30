package com.example.healthtracker.ui.meal

import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.testutil.FakeMacroAnalyzer
import com.example.healthtracker.testutil.FakeMealRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.testutil.MutableClock
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.TimeError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MealViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val aiMacros = Macros(540, 32, 58, 18)
    private val analyzer = FakeMacroAnalyzer(MacroAnalysisResult.Success(aiMacros))
    private val repository = FakeMealRepository()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { MealViewModel(analyzer, repository, MutableClock(now)) }

    private val state get() = viewModel.uiState.value

    private fun TestScope.analyzed(text: String = "chicken and rice") {
        viewModel.onInputChange(text)
        viewModel.analyze()
        advanceUntilIdle()
    }

    // region Analysis

    @Test
    fun analyze_showsLoadingThenReviewOverlay() = runTest {
        viewModel.onInputChange("  200g chicken, 1 cup rice  ")
        viewModel.analyze()
        assertThat(state.analysis).isEqualTo(AnalysisState.Loading)
        assertThat(state.canAnalyze).isFalse()

        advanceUntilIdle()

        assertThat(state.analysis).isEqualTo(AnalysisState.Ready(aiMacros))
        assertThat(analyzer.requests).containsExactly("200g chicken, 1 cup rice")
    }

    @Test
    fun analyze_mapsFailuresToErrors() = runTest {
        viewModel.onInputChange("toast")

        analyzer.result = MacroAnalysisResult.NetworkError(IOException())
        viewModel.analyze()
        advanceUntilIdle()
        assertThat(state.analysis).isEqualTo(AnalysisState.Error(AnalysisError.NETWORK))

        analyzer.result = MacroAnalysisResult.MalformedResponse(IllegalStateException())
        viewModel.analyze() // retry allowed from the error state
        advanceUntilIdle()
        assertThat(state.analysis).isEqualTo(AnalysisState.Error(AnalysisError.MALFORMED_RESPONSE))
    }

    @Test
    fun analyze_ignoresBlankInput() = runTest {
        viewModel.onInputChange("   ")
        viewModel.analyze()
        advanceUntilIdle()
        assertThat(analyzer.requests).isEmpty()
    }

    @Test
    fun editingTextDuringAnalysis_discardsStaleResult() = runTest {
        viewModel.onInputChange("pasta")
        viewModel.analyze()
        viewModel.onInputChange("pasta with pesto")
        advanceUntilIdle()
        assertThat(state.analysis).isEqualTo(AnalysisState.Idle)
    }

    // endregion
    // region Review overlay

    @Test
    fun saveAsIs_persistsAiMacrosAndResets() = runTest {
        analyzed()
        viewModel.save()

        val saved = repository.meals.value.single()
        assertThat(saved.description).isEqualTo("chicken and rice")
        assertThat(saved.macros).isEqualTo(aiMacros)
        assertThat(saved.eatenAt).isEqualTo(now)
        assertThat(state).isEqualTo(MealUiState(message = MealMessage.Saved(saved.id, aiMacros, edited = false)))
    }

    @Test
    fun edit_prefillsAiValues_andSavesCorrectedMacros() = runTest {
        analyzed()
        viewModel.startEditing()
        val draft = (state.analysis as AnalysisState.Ready).draft!!
        assertThat(draft.values[MacroField.CARBS]).isEqualTo("58")

        viewModel.onMacroFieldChange(MacroField.CARBS, "72,4")
        viewModel.save()

        val expected = aiMacros.copy(carbsGrams = 72)
        assertThat(repository.meals.value.single().macros).isEqualTo(expected)
        assertThat(state.message).isEqualTo(MealMessage.Saved(1, expected, edited = true))
    }

    @Test
    fun edit_invalidFieldsBlockSave() = runTest {
        analyzed()
        viewModel.startEditing()
        viewModel.onMacroFieldChange(MacroField.CARBS, "")
        viewModel.onMacroFieldChange(MacroField.FAT, "lots")
        viewModel.save()

        assertThat(repository.meals.value).isEmpty()
        val errors = (state.analysis as AnalysisState.Ready).draft!!.errors
        assertThat(errors).containsExactly(MacroField.CARBS, InputError.EMPTY, MacroField.FAT, InputError.NOT_A_NUMBER)

        viewModel.onMacroFieldChange(MacroField.CARBS, "60")
        assertThat((state.analysis as AnalysisState.Ready).draft!!.errors).containsExactly(MacroField.FAT, InputError.NOT_A_NUMBER)
    }

    @Test
    fun cancelEditing_returnsToAiValues() = runTest {
        analyzed()
        viewModel.startEditing()
        viewModel.onMacroFieldChange(MacroField.CARBS, "99")
        viewModel.cancelEditing()
        viewModel.save()
        assertThat(repository.meals.value.single().macros).isEqualTo(aiMacros)
    }

    @Test
    fun dismissReview_keepsTextForReanalysis() = runTest {
        analyzed("chicken and rice")
        viewModel.dismissReview()
        assertThat(state.analysis).isEqualTo(AnalysisState.Idle)
        assertThat(state.input).isEqualTo("chicken and rice")
        assertThat(state.canAnalyze).isTrue()
        assertThat(repository.meals.value).isEmpty()
    }

    @Test
    fun save_failureKeepsOverlayForRetry() = runTest {
        analyzed()
        repository.failWrites = true
        viewModel.save()

        assertThat(state.message).isEqualTo(MealMessage.SaveFailed)
        assertThat(state.analysis).isEqualTo(AnalysisState.Ready(aiMacros))
        assertThat(state.isSaving).isFalse()
    }

    // endregion
    // region Back-dating

    @Test
    fun backdatedMeal_isStoredAtChosenTime() = runTest {
        val lunch = now.minus(Duration.ofHours(3))
        analyzed()
        viewModel.onEatenAtChange(lunch)
        viewModel.save()
        assertThat(repository.meals.value.single().eatenAt).isEqualTo(lunch)
    }

    @Test
    fun futureMealTime_isRejected() = runTest {
        analyzed()
        viewModel.onEatenAtChange(now.plus(Duration.ofMinutes(30)))
        viewModel.save()

        assertThat(repository.meals.value).isEmpty()
        assertThat(state.timeError).isEqualTo(TimeError.IN_FUTURE)

        viewModel.onEatenAtChange(null)
        assertThat(state.timeError).isNull()
    }

    // endregion
}
