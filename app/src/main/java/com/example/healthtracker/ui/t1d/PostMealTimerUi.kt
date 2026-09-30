package com.example.healthtracker.ui.t1d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.TimerTrigger
import com.example.healthtracker.ui.common.Card
import com.example.healthtracker.ui.common.formatRemaining
import com.example.healthtracker.ui.common.formatTime
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun StartTimerDialog(
    prompt: PostMealTimer,
    replacesExisting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
        title = { Text("Start 2-hour post-meal timer?") },
        text = {
            val from = when (prompt.trigger) {
                TimerTrigger.NOVORAPID_DOSE -> "your Novorapid dose"
                TimerTrigger.PRE_MEAL_GLUCOSE -> "your pre-meal reading"
            }
            Text(
                "You'll be reminded at ${formatTime(prompt.fireAt)} (2 hours after $from) to check your glucose." +
                    if (replacesExisting) "\n\nThis replaces your current reminder." else "",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Start timer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun CorrectionCheckDialog(fireAt: Instant, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
        title = { Text("Remind you to check in 3 hours?") },
        text = {
            Text(
                "By ${formatTime(fireAt)}, NovoRapid has done most of its work. That reading shows how well your " +
                    "correction factor fits, and the Insights tab uses it to suggest changes.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Remind me") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun ActiveTimerCard(
    title: String,
    fireAt: Instant,
    permissions: ReminderPermissionState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(Instant.now(), fireAt) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val remaining = Duration.between(now, fireAt)

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$title at ${formatTime(fireAt)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (remaining.isNegative) "Due now" else "in ${formatRemaining(remaining)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            if (!permissions.notificationsAllowed) {
                PermissionWarning(
                    text = "Notifications are off, so the reminder can't appear.",
                    action = "Turn on",
                    onAction = permissions::openNotificationSettings,
                )
            }
            if (!permissions.exactAlarmsAllowed) {
                PermissionWarning(
                    text = "Exact alarms aren't allowed, so the reminder may arrive late.",
                    action = "Allow",
                    onAction = permissions::openExactAlarmSettings,
                )
            }
        }
    }
}

@Composable
private fun PermissionWarning(text: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAction) { Text(action) }
    }
}
