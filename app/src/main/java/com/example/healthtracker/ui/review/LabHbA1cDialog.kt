package com.example.healthtracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.NumberField
import com.example.healthtracker.ui.common.formatDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Value (in % or mmol/mol) and the date of the lab test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabHbA1cDialog(
    form: LabForm,
    today: LocalDate,
    onValueChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lab HbA1c") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "From your latest blood test. Enter it as a percentage (like 7.1) or in mmol/mol (like 54).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                NumberField(
                    value = form.value,
                    onValueChange = onValueChange,
                    label = "HbA1c",
                    error = when (form.error) {
                        LabError.EMPTY -> InputError.EMPTY
                        LabError.NOT_A_NUMBER -> InputError.NOT_A_NUMBER
                        LabError.OUT_OF_RANGE -> InputError.OUT_OF_RANGE
                        LabError.FUTURE_DATE, null -> null
                    },
                    outOfRangeText = "Enter 4–15 % or 20–140 mmol/mol",
                    imeAction = ImeAction.Done,
                    onImeAction = onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Test date", style = MaterialTheme.typography.labelLarge)
                AssistChip(
                    onClick = { pickingDate = true },
                    label = { Text(formatDay(form.date, today)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                )
                if (form.error == LabError.FUTURE_DATE) {
                    Text("The test date can't be in the future", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        // DatePicker works in UTC-midnight millis for the selected calendar day.
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        pickingDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}
