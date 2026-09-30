package com.example.healthtracker.ui.profile

import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.testutil.FakeSettingsRepository
import com.example.healthtracker.testutil.MainDispatcherRule
import com.example.healthtracker.ui.common.InputError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyGoalsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val viewModel by lazy { DailyGoalsViewModel(settings) }
    private val state get() = viewModel.uiState.value

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun showsDefaults_andSavesEdits() = runTest {
        subscribe()
        assertThat(state.values[Goal.CARBS]).isEqualTo("200")
        assertThat(state.isEditing).isFalse()

        viewModel.onChange(Goal.CARBS, "150")
        assertThat(state.isEditing).isTrue()
        viewModel.save()

        assertThat(settings.dailyGoals.value).isEqualTo(SettingsRepository.DEFAULT_DAILY_GOALS.copy(carbsGrams = 150))
        assertThat(state.saved).isTrue()
        assertThat(state.isEditing).isFalse()
    }

    @Test
    fun rejectsImplausibleGoals() = runTest {
        subscribe()
        viewModel.onChange(Goal.CALORIES, "50")
        viewModel.onChange(Goal.FAT, "")
        viewModel.save()

        assertThat(state.errors).containsExactly(Goal.CALORIES, InputError.OUT_OF_RANGE, Goal.FAT, InputError.EMPTY)
        assertThat(settings.dailyGoals.value).isEqualTo(SettingsRepository.DEFAULT_DAILY_GOALS)
    }

}
