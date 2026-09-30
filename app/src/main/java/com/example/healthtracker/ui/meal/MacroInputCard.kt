package com.example.healthtracker.ui.meal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthtracker.ui.common.AiButton
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.CardShape
import com.example.healthtracker.ui.common.CardTitle
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.ShimmerBar
import com.example.healthtracker.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun MacroInputCard(
    input: String,
    analysis: AnalysisState,
    canAnalyze: Boolean,
    onInputChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = analysis is AnalysisState.Loading
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard {
            CardTitle("What did you eat?", icon = Icons.Outlined.AutoAwesome, tint = AppColors.Cyan)
            MutedText("Describe a meal or paste a whole recipe. The AI estimates the macros, then you check them before saving.")
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("e.g. 200 g chicken breast, 1 cup cooked rice, side salad with olive oil") },
                minLines = 6,
                enabled = !isLoading,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Violet,
                    unfocusedBorderColor = AppColors.Border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (analysis) {
            AnalysisState.Loading -> AnalyzingCard(onCancel)
            is AnalysisState.Error -> ErrorCard(analysis.reason)
            else -> Unit
        }
        if (!isLoading) {
            AiButton(
                if (analysis is AnalysisState.Error) "Try again" else "Analyze with AI",
                onClick = onAnalyze,
                enabled = canAnalyze,
                icon = Icons.Outlined.AutoAwesome,
            )
        }
    }
}

private val ANALYZING_STEPS = listOf("Reading recipe…", "Identifying ingredients…", "Estimating portions…", "Calculating macros…")

/** Skeleton loader with progress text, so the wait feels like work being done rather than a hang. */
@Composable
private fun AnalyzingCard(onCancel: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < ANALYZING_STEPS.lastIndex) {
            delay(1_800)
            step++
        }
    }
    AppCard(Modifier.border(1.dp, AppColors.Violet.copy(alpha = 0.5f), CardShape)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ANALYZING_STEPS[step], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        ShimmerBar(Modifier.fillMaxWidth(0.55f), height = 28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBar(Modifier.weight(1f), height = 36.dp)
            ShimmerBar(Modifier.weight(1f), height = 36.dp)
            ShimmerBar(Modifier.weight(1f), height = 36.dp)
        }
        ShimmerBar(Modifier.fillMaxWidth(), height = 12.dp)
        ShimmerBar(Modifier.fillMaxWidth(0.8f), height = 12.dp)
    }
}

@Composable
private fun ErrorCard(reason: AnalysisError) {
    AppCard(Modifier.border(1.dp, AppColors.Urgent.copy(alpha = 0.6f), CardShape)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = AppColors.Urgent)
            Text(
                when (reason) {
                    AnalysisError.NETWORK -> "Couldn't reach the analysis service. Check your connection and try again."
                    AnalysisError.MALFORMED_RESPONSE -> "The AI returned an answer we couldn't read. Try again or rephrase the meal."
                    AnalysisError.NOT_CONFIGURED ->
                        "The AI isn't set up: add your API key to local.properties (gemini.apiKey=… or anthropic.apiKey=…) and rebuild the app."
                    AnalysisError.RATE_LIMITED ->
                        "The AI's usage limit was reached. Wait a minute and try again."
                    AnalysisError.SERVICE_BUSY ->
                        "The AI service is busy right now. Try again in a minute."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
