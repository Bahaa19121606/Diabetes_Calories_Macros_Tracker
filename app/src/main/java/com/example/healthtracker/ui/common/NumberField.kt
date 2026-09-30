package com.example.healthtracker.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

fun InputError.message(outOfRange: String): String = when (this) {
    InputError.EMPTY -> "Required"
    InputError.NOT_A_NUMBER -> "Enter a number"
    InputError.OUT_OF_RANGE -> outOfRange
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: InputError?,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    outOfRangeText: String = "Out of range",
    hint: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val supporting = error?.message(outOfRangeText) ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        // Only Done is overridden; Next keeps the default move-focus behaviour.
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        modifier = modifier,
    )
}
