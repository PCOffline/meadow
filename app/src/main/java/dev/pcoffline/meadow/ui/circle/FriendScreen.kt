package dev.pcoffline.meadow.ui.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.SharedUsage
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.limitText
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.flow.StateFlow

/** What a Bumblebee can see about a friend they look after. */
class FriendViewModel(uid: String) : ViewModel() {
    val friend: StateFlow<UserProfile?> = AppGraph.social.profile(uid).stateWhileVisible(this, null)
    val usage: StateFlow<SharedUsage?> = AppGraph.share.usage(uid, Days.todayKey()).stateWhileVisible(this, null)
    val limits: StateFlow<List<AppLimit>> = AppGraph.share.limits(uid).stateWhileVisible(this, emptyList())
}

@Composable
fun FriendScreen(
    me: UserProfile,
    uid: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: FriendViewModel = viewModel(key = "friend-$uid") { FriendViewModel(uid) },
) {
    val friend by viewModel.friend.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val limits by viewModel.limits.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(friend?.displayName.orEmpty(), onBack = onBack)
        val f = friend
        if (f == null) {
            Loading()
        } else {
            FriendDetails(f, usage, limits, onBack, onChat = { onOpenChat(ChatRepository.chatIdFor(me.uid, f.uid)) })
        }
    }
}

@Composable
private fun FriendDetails(f: UserProfile, usage: SharedUsage?, limits: List<AppLimit>, onBack: () -> Unit, onChat: () -> Unit) {
    var confirmStepDown by remember { mutableStateOf(false) }
    val action = rememberAction()
    val steppedDown = stringResource(R.string.friend_stepped_down)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowerAvatar(f.flower, size = 64.dp, tint = MaterialTheme.colorScheme.secondaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (f.strictMode) R.string.friend_strict_on else R.string.friend_strict_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onChat) { Text(stringResource(R.string.action_chat)) }
        }

        MeadowCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.friend_protections), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            listOf(
                "usageAccess" to R.string.setup_usage_title,
                "accessibility" to R.string.setup_keeper_title,
                "deviceAdmin" to R.string.setup_admin_title,
            ).forEach { (key, label) ->
                val on = f.protection[key]
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(
                            when (on) {
                                true -> R.string.friend_protection_on
                                false -> R.string.friend_protection_off
                                null -> R.string.friend_protection_unknown
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val now = System.currentTimeMillis()
            val used = Emergency.ALLOWANCE - f.emergenciesLeft
            Text(
                if (f.emergencyUntilMillis > now) stringResource(R.string.friend_emergency_active, durationText(f.emergencyUntilMillis - now))
                else stringResource(R.string.friend_emergency_used, used, Emergency.ALLOWANCE),
                style = MaterialTheme.typography.bodySmall,
                color = if (used > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                f.lastHeartbeatMillis?.let { stringResource(R.string.friend_checked_in, relativeTime(it)) }
                    ?: stringResource(R.string.friend_never_checked_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        MeadowCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.friend_today), style = MaterialTheme.typography.titleMedium)
            if (usage == null) {
                Text(stringResource(R.string.friend_no_usage), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.friend_screen_time, minutesText(usage.totalMinutes)), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                val max = usage.apps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                usage.apps.take(8).forEach { app ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(app.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    if (app.limitMinutes > 0) stringResource(R.string.friend_used_of, minutesText(app.minutes), minutesText(app.limitMinutes))
                                    else minutesText(app.minutes),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            GrowthBar(
                                if (app.limitMinutes > 0) app.minutes / app.limitMinutes.toFloat() else app.minutes / max.toFloat(),
                                height = 6.dp,
                                color = if (app.limitMinutes > 0) null else Garden.colors.leaf,
                            )
                        }
                    }
                }
            }
        }

        MeadowCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.friend_limits), style = MaterialTheme.typography.titleMedium)
            if (limits.isEmpty()) Text(stringResource(R.string.friend_no_limits), style = MaterialTheme.typography.bodySmall)
            limits.forEach { limit ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(limit.packageName, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(limit.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(limitText(limit.dailyMinutes, limit.weeklyMinutes), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        ErrorNote(action.error)
        OutlinedButton(onClick = { confirmStepDown = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.friend_step_down, f.displayName))
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmStepDown) {
        AlertDialog(
            onDismissRequest = { confirmStepDown = false },
            title = { Text(stringResource(R.string.friend_step_down_title)) },
            text = { Text(stringResource(R.string.friend_step_down_body, f.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStepDown = false
                    action.run(successMessage = steppedDown, onSuccess = onBack) { AppGraph.social.stepDown(f.uid) }
                }) { Text(stringResource(R.string.friend_step_down_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmStepDown = false }) { Text(stringResource(R.string.friend_step_down_stay)) } },
        )
    }
}
