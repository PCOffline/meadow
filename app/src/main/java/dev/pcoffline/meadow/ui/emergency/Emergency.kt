@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.rememberAction
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CONFIRM_DELAY_SECONDS = 5

/**
 * Lifts limits right away without an approver. Deliberately a little slow to confirm: a reason
 * is required and the button only unlocks after a few seconds.
 */
@Composable
fun EmergencyDialog(packageName: String?, appName: String?, onDismiss: () -> Unit) {
    val profileState by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val profile = (profileState as? ProfileState.Ready)?.profile
    var onlyThisApp by rememberSaveable { mutableStateOf(packageName != null) }
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    var reason by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(CONFIRM_DELAY_SECONDS) }
    val action = rememberAction()
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1_000L)
            countdown--
        }
    }
    val left = profile?.emergenciesLeft
    val usedUp = left == 0
    val reasonOk = reason.trim().length >= 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚨 Emergency unlock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "For real emergencies — finding your way, reaching someone, a crisis at work. Limits lift " +
                        "right away with no approval, and your approvers are told immediately, with your reason.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when {
                        left == null -> "You're signed out, so this uses your one offline emergency unlock for the month."
                        usedUp -> "You've used all ${Emergency.ALLOWANCE} emergency unlocks for the last 30 days. Ask an approver for time instead."
                        else -> "You have $left of ${Emergency.ALLOWANCE} left in the last 30 days."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (usedUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
                if (!usedUp) {
                    if (packageName != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = onlyThisApp, onClick = { onlyThisApp = true }, label = { Text("Just ${appName ?: "this app"}") })
                            FilterChip(selected = !onlyThisApp, onClick = { onlyThisApp = false }, label = { Text("All apps") })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Emergency.MINUTE_OPTIONS.forEach {
                            FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(formatMinutes(it)) })
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(280) },
                        label = { Text("What's the emergency?") },
                        supportingText = { if (!reasonOk) Text("A few words — your approvers will see this.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !usedUp && reasonOk && countdown == 0 && !action.busy,
                onClick = {
                    val scope = if (onlyThisApp) packageName else null
                    action.run(onSuccess = onDismiss) {
                        val result = AppGraph.emergency.unlock(minutes, reason, scope, if (onlyThisApp) appName else null)
                        val time = Instant.ofEpochMilli(result.untilMillis).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                        action.toast(
                            if (result.offline) "Unlocked until $time. Your approvers will be told once you're back online."
                            else "Unlocked until $time. Your approvers have been told.",
                        )
                    }
                },
            ) {
                Text(
                    when {
                        action.busy -> "Unlocking…"
                        countdown > 0 -> "Unlock ($countdown)"
                        else -> "Unlock now",
                    },
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown while an emergency unlock is running, with a way to end it early. */
@Composable
fun EmergencyBanner(modifier: Modifier = Modifier) {
    val emergency by AppGraph.store.emergency.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(emergency) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val e = emergency?.takeIf { it.isActive(now) } ?: return
    val action = rememberAction()
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🚨", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Emergency unlock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "${if (e.packageName == null) "All limits" else AppGraph.catalog.label(e.packageName) + "'s limit"} " +
                        "paused for ${formatDuration(e.untilMillis - now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = { action.run(successMessage = "Limits are back 🌿") { AppGraph.emergency.end() } }) {
                Text("End now")
            }
        }
    }
}
