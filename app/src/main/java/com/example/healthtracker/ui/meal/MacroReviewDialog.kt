package com.example.healthtracker.ui.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthtracker.domain.calibration.MealResponseAnalyzer
import com.example.healthtracker.domain.model.Macros
import com.example.healthtracker.ui.common.AppCard
import com.example.healthtracker.ui.common.MutedText
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.OccurredAtField
import com.example.healthtracker.ui.common.Pill
import com.example.healthtracker.ui.common.PrimaryButton
import com.example.healthtracker.ui.common.SecondaryButton
import com.example.healthtracker.ui.common.TimeError
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.theme.BigNumber
import java.time.Instant

/**
 * Food summary shown once the AI returns: the estimate plus "Save / Edit". Carbs drive the
 * insulin dose, so the user always confirms (or corrects) the numbers before they're stored.
 * Swiping away is disabled so a paid analysis isn't lost by accident; Discard is explicit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MacroReviewDialog(
    description: String,
    ready: AnalysisState.Ready,
    eatenAt: Instant?,
    timeError: TimeError?,
    isSaving: Boolean,
    onEatenAtChange: (Instant?) -> Unit,
    onEdit: () -> Unit,
    onFieldChange: (MacroField, String) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val draft = ready.draft
    val macros = ready.aiMacros
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDiscard() },
        sheetState = sheetState,
        containerColor = AppColors.Card,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = AppColors.Cyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (draft == null) "AI estimate" else "Edit macros", style = MaterialTheme.typography.labelLarge, color = AppColors.Cyan)
            }
            Text(
                description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (draft == null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${macros.calories}", style = BigNumber)
                    Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                MacroPills(macros)
                if (macros.fatGrams > MealResponseAnalyzer.MAX_FAT_GRAMS || macros.proteinGrams > MealResponseAnalyzer.MAX_PROTEIN_GRAMS) {
                    DelayedRiseWarning()
                }
                ready.notes?.takeIf { it.isNotBlank() }?.let { MutedText(it) }
            } else {
                MacroEditor(draft, macros, enabled = !isSaving, onFieldChange = onFieldChange, onDone = onSave)
            }

            OccurredAtField(label = "Eaten", value = eatenAt, error = timeError, onValueChange = onEatenAtChange, enabled = !isSaving)

            PrimaryButton("Save meal", onClick = onSave, enabled = !isSaving)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (draft == null) {
                    SecondaryButton("Edit values", onClick = onEdit, enabled = !isSaving, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDiscard, enabled = !isSaving) { Text("Discard") }
                } else {
                    TextButton(onClick = onCancelEdit, enabled = !isSaving) { Text("Back to the AI estimate") }
                }
            }
        }
    }
}

/** The three macros as high-contrast pills; carbs are emphasized because they drive the dose. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroPills(macros: Macros, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("${macros.carbsGrams} g Carbs", AppColors.Carbs, emphasized = true)
        Pill("${macros.proteinGrams} g Protein", AppColors.Protein)
        Pill("${macros.fatGrams} g Fat", AppColors.Fat)
    }
}

/** Fat and protein slow digestion, so glucose can keep rising for hours after a normal bolus has peaked. */
@Composable
fun DelayedRiseWarning(modifier: Modifier = Modifier) {
    AppCard(modifier, color = AppColors.Warning.copy(alpha = 0.12f), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AppColors.Warning)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Expect a delayed rise", style = MaterialTheme.typography.titleSmall, color = AppColors.Warning, fontWeight = FontWeight.Bold)
                Text(
                    "This meal is high in fat or protein, so glucose may keep rising 3–5 hours later. " +
                        "If your care team has shown you how, consider splitting the dose.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MacroEditor(
    draft: MacroDraft,
    aiMacros: Macros,
    enabled: Boolean,
    onFieldChange: (MacroField, String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MacroField.entries.forEach { field ->
            val (label, suffix, aiValue) = when (field) {
                MacroField.CALORIES -> Triple("Calories", "kcal", aiMacros.calories)
                MacroField.CARBS -> Triple("Carbs", "g", aiMacros.carbsGrams)
                MacroField.PROTEIN -> Triple("Protein", "g", aiMacros.proteinGrams)
                MacroField.FAT -> Triple("Fat", "g", aiMacros.fatGrams)
            }
            val isLast = field == MacroField.entries.last()
            NumberField(
                value = draft.values[field].orEmpty(),
                onValueChange = { onFieldChange(field, it) },
                label = label,
                suffix = suffix,
                error = draft.errors[field],
                outOfRangeText = "Must be 0–${field.max.toInt()}",
                hint = "AI estimate: $aiValue $suffix",
                enabled = enabled,
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                onImeAction = { if (isLast) onDone() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
