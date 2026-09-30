package com.example.healthtracker.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private enum class PickerStep { NONE, DATE, TIME }

/**
 * "Eaten: Now ▾" chip for back-dating a log entry. Opens a date picker (past days only)
 * followed by a time picker. [value] null means "now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccurredAtField(
    label: String,
    value: Instant?,
    error: TimeError?,
    onValueChange: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var step by remember { mutableStateOf(PickerStep.NONE) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label:", style = MaterialTheme.typography.bodyMedium)
            AssistChip(
                onClick = { step = PickerStep.DATE },
                enabled = enabled,
                label = { Text(value?.let { formatDateTime(it, zone) } ?: "Now") },
                leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            )
            if (value != null) {
                TextButton(onClick = { onValueChange(null) }, enabled = enabled) { Text("Use now") }
            }
        }
        if (error == TimeError.IN_FUTURE) {
            Text(
                "Time can't be in the future",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val initial = (value ?: Instant.now()).atZone(zone)

    when (step) {
        PickerStep.NONE -> Unit
        PickerStep.DATE -> {
            // DatePicker works in UTC-midnight millis for the selected calendar day.
            val todayUtcMillis = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
                },
            )
            DatePickerDialog(
                onDismissRequest = { step = PickerStep.NONE },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDate = dateState.selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            step = if (pickedDate != null) PickerStep.TIME else PickerStep.NONE
                        },
                    ) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = { step = PickerStep.NONE }) { Text("Cancel") } },
            ) {
                DatePicker(state = dateState)
            }
        }
        PickerStep.TIME -> {
            val timeState = rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = DateFormat.is24HourFormat(LocalContext.current),
            )
            AlertDialog(
                onDismissRequest = { step = PickerStep.NONE },
                title = { Text("Select time") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickedDate?.let { date ->
                                onValueChange(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                            }
                            step = PickerStep.NONE
                        },
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { step = PickerStep.NONE }) { Text("Cancel") } },
            )
        }
    }
}
