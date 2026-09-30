package com.example.healthtracker.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

data class SnackbarSpec(
    val text: String,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
)

/**
 * Shows a one-off ViewModel message as a snackbar, then marks it consumed. Consuming only
 * after the snackbar finishes keeps the effect's key stable while it is on screen.
 */
@Composable
fun <T : Any> SnackbarMessageEffect(
    message: T?,
    hostState: SnackbarHostState,
    onShown: () -> Unit,
    render: (T) -> SnackbarSpec,
) {
    val currentOnShown by rememberUpdatedState(onShown)
    val currentRender by rememberUpdatedState(render)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val spec = currentRender(current)
        val result = hostState.showSnackbar(
            message = spec.text,
            actionLabel = spec.actionLabel,
            withDismissAction = spec.actionLabel != null,
            duration = if (spec.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) spec.onAction()
        currentOnShown()
    }
}
