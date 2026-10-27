@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.emergency

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.EmergencyRepository
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.util.Date

private const val CONFIRM_DELAY_SECONDS = 5

/**
 * Lifts limits right away without a Bumblebee. Deliberately a little slow to confirm: a reason
 * is required and the button only unlocks after a few seconds.
 */
@Composable
fun EmergencyDialog(packageName: String?, appName: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val profileState by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val profile = (profileState as? ProfileState.Ready)?.profile
    var onlyThisApp by rememberSaveable { mutableStateOf(packageName != null) }
    var minutes by rememberSaveable { mutableIntStateOf(Emergency.MINUTE_OPTIONS.first()) }
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
    val missing = (EmergencyRepository.MIN_REASON_LENGTH - reason.trim().length).coerceAtLeast(0)
    val reasonOk = missing == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // First, so a failed unlock is seen even with the keyboard covering the bottom.
                ErrorNote(action.error)
                Text(stringResource(R.string.emergency_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        left == null -> stringResource(R.string.emergency_dialog_signed_out)
                        usedUp -> pluralStringResource(R.plurals.emergency_dialog_used_up, Emergency.ALLOWANCE, Emergency.ALLOWANCE)
                        else -> pluralStringResource(R.plurals.emergency_dialog_left, left, left, Emergency.ALLOWANCE)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (usedUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
                if (!usedUp) {
                    if (packageName != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = onlyThisApp,
                                onClick = { onlyThisApp = true },
                                label = { Text(stringResource(R.string.emergency_dialog_just_app, appName ?: stringResource(R.string.request_an_app))) },
                            )
                            FilterChip(
                                selected = !onlyThisApp,
                                onClick = { onlyThisApp = false },
                                label = { Text(stringResource(R.string.emergency_dialog_all_apps)) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Emergency.MINUTE_OPTIONS.forEach {
                            FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(minutesText(it)) })
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(280) },
                        label = { Text(stringResource(R.string.emergency_dialog_reason)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        // Says what's still needed: the unlock button stays off until then.
                        supportingText = if (reasonOk) null else {
                            { Text(pluralStringResource(R.plurals.emergency_dialog_reason_needed, missing, missing)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !usedUp && reasonOk && countdown == 0 && !action.busy,
                // Through the button's colours, so it greys out while it can't be pressed.
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = {
                    val scope = if (onlyThisApp) packageName else null
                    action.run(onSuccess = onDismiss) {
                        val result = AppGraph.emergency.unlock(minutes, reason, scope, if (onlyThisApp) appName else null)
                        val time = DateFormat.getTimeFormat(context).format(Date(result.untilMillis))
                        action.toast(
                            resources.getString(
                                if (result.offline) R.string.emergency_unlocked_offline else R.string.emergency_unlocked,
                                time,
                            ),
                        )
                    }
                },
            ) {
                Text(
                    when {
                        action.busy -> stringResource(R.string.emergency_dialog_unlocking)
                        countdown > 0 -> stringResource(R.string.emergency_dialog_unlock_countdown, countdown)
                        else -> stringResource(R.string.emergency_dialog_unlock)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** An active emergency unlock, ready to display. */
data class ActiveEmergency(val appLabel: String?, val remainingMs: Long)

class EmergencyBannerViewModel : ViewModel() {
    /** Refreshed every 15s so the countdown stays roughly right; null when no unlock is on. */
    val active: StateFlow<ActiveEmergency?> = combine(AppGraph.store.emergency, ticker(15_000L)) { emergency, _ ->
        val now = System.currentTimeMillis()
        emergency?.takeIf { it.isActive(now) }?.let { e ->
            ActiveEmergency(appLabel = e.packageName?.let(AppGraph.catalog::label), remainingMs = e.untilMillis - now)
        }
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, null)
}

/** Shown while an emergency unlock is on, with a way to end it early. */
@Composable
fun EmergencyBanner(modifier: Modifier = Modifier, viewModel: EmergencyBannerViewModel = viewModel { EmergencyBannerViewModel() }) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    // Before the banner can go: ending the unlock removes it, and the action has to stay
    // attached to say the limits are back.
    val action = rememberAction()
    val ended = stringResource(R.string.emergency_ended)
    val e = active ?: return
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🚨", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    if (e.appLabel == null) stringResource(R.string.emergency_banner_all, durationText(e.remainingMs))
                    else stringResource(R.string.emergency_banner_app, e.appLabel, durationText(e.remainingMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = { action.run(successMessage = ended) { AppGraph.emergency.end() } }) {
                Text(stringResource(R.string.emergency_banner_end))
            }
        }
        // Otherwise a failed attempt to end it would look like a button that does nothing.
        ErrorNote(action.error)
    }
}
