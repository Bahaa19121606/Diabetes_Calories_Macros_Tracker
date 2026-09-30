package com.example.healthtracker.ui.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthtracker.ui.common.SnackbarMessageEffect
import com.example.healthtracker.ui.common.SnackbarSpec

@Composable
fun MealScreen(
    snackbarHostState: SnackbarHostState,
    onCalculateDose: (mealId: Long) -> Unit,
    viewModel: MealViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SnackbarMessageEffect(state.message, snackbarHostState, viewModel::messageShown) { message ->
        when (message) {
            is MealMessage.Saved -> SnackbarSpec(
                "Meal saved · ${message.macros.calories} kcal, ${message.macros.carbsGrams} g carbs" +
                    if (message.edited) " (edited)" else "",
                actionLabel = "Dose",
                onAction = { onCalculateDose(message.mealId) },
            )
            MealMessage.SaveFailed -> SnackbarSpec("Couldn't save the meal. Please try again.")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MacroInputCard(
            input = state.input,
            analysis = state.analysis,
            canAnalyze = state.canAnalyze,
            onInputChange = viewModel::onInputChange,
            onAnalyze = viewModel::analyze,
            onCancel = viewModel::cancelAnalysis,
        )
    }

    (state.analysis as? AnalysisState.Ready)?.let { ready ->
        MacroReviewDialog(
            description = state.input,
            ready = ready,
            eatenAt = state.eatenAt,
            timeError = state.timeError,
            isSaving = state.isSaving,
            onEatenAtChange = viewModel::onEatenAtChange,
            onEdit = viewModel::startEditing,
            onFieldChange = viewModel::onMacroFieldChange,
            onCancelEdit = viewModel::cancelEditing,
            onSave = viewModel::save,
            onDiscard = viewModel::dismissReview,
        )
    }
}
