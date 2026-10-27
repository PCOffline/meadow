package dev.pcoffline.meadow.ui.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.runCatchingCancellable
import dev.pcoffline.meadow.enforcement.PhoneMaker
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.friendlyMessage
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.launch

/** What a test notification found. */
sealed interface PushTest {
    /** Google took it for at least one of this person's phones. */
    data object Sent : PushTest

    /** This phone couldn't register, so nothing can reach it. */
    data object NoPhone : PushTest

    /** Google refused every phone, with its [reasons]. */
    data class Undelivered(val reasons: List<String>) : PushTest

    /** The test couldn't be asked for at all. */
    data class Failed(val message: String) : PushTest

    companion object {
        fun of(phones: Int, delivered: Int, errors: List<String>): PushTest = when {
            delivered > 0 -> Sent
            phones == 0 -> NoPhone
            else -> Undelivered(errors.map { it.removePrefix("messaging/") }.distinct())
        }
    }
}

/** Checks that chats and requests reach this phone, and steps that keep Android, and the phone maker's own battery savers, from putting Meadow to sleep. */
@Composable
fun TroubleshootScreen(onBack: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppGraph.protection.current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = AppGraph.protection.current() }
    val brand = remember { PhoneMaker.brand() }
    val steps = remember { PhoneMaker.steps(context, brand) }
    var silent by remember { mutableStateOf(AppGraph.notifier.silentMessageChannels()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { silent = AppGraph.notifier.silentMessageChannels() }
    TroubleshootContent(
        state = state,
        steps = steps,
        silentChannels = silent,
        onNotificationSettings = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onTestPush = {
            if (AppGraph.backend.uid == null) {
                PushTest.Failed(context.getString(R.string.error_sign_in_first))
            } else if (runCatchingCancellable { AppGraph.sync.registerThisPhone() }.isFailure) {
                PushTest.NoPhone
            } else {
                runCatchingCancellable {
                    val result = AppGraph.backend.call("sendTestPush")
                    PushTest.of(
                        phones = (result["phones"] as? Number)?.toInt() ?: 0,
                        delivered = (result["delivered"] as? Number)?.toInt() ?: 0,
                        errors = (result["errors"] as? List<*>).orEmpty().filterIsInstance<String>(),
                    )
                }.getOrElse { PushTest.Failed(it.friendlyMessage(context)) }
            }
        },
        onBack = onBack,
        onOpenSetup = onOpenSetup,
        onOpen = { PhoneMaker.open(context, it) },
        onMoreHelp = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, PhoneMaker.helpUrl(brand).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
}

/** The troubleshooting steps, drawn from plain state (also used for previews and tests). */
@Composable
fun TroubleshootContent(
    state: ProtectionState,
    steps: List<PhoneMaker.Step>,
    silentChannels: List<String>? = emptyList(),
    onNotificationSettings: () -> Unit = {},
    onTestPush: suspend () -> PushTest = { PushTest.Sent },
    onBack: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpen: (PhoneMaker.Step) -> Unit = {},
    onMoreHelp: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.trouble_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.essentialsReady) {
                MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.trouble_essentials), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_protections_button))
                    }
                }
            }
            NotificationsCard(silentChannels, onNotificationSettings, onTestPush)
            steps.forEachIndexed { i, step ->
                val done = step.key == "battery" && state.batteryUnrestricted
                MeadowCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.trouble_step, i + 1, stringResource(step.title)),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (done) Pill(stringResource(R.string.setup_step_done), MaterialTheme.colorScheme.primaryContainer)
                    }
                    Text(
                        stringResource(step.body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!done && step.screens.isNotEmpty()) {
                        FilledTonalButton(onClick = { onOpen(step) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.trouble_open))
                        }
                    }
                }
            }
            TextButton(onClick = onMoreHelp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.trouble_more_help))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Whether chats and requests can reach this phone: notifications turned off, on this phone or
 * per kind, are named with a way to turn them on; otherwise a test proves the whole way works.
 * [silentChannels] is null when all of Meadow's notifications are off.
 */
@Composable
private fun NotificationsCard(silentChannels: List<String>?, onSettings: () -> Unit, onTest: suspend () -> PushTest) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PushTest?>(null) }
    val blocked = silentChannels == null || silentChannels.isNotEmpty()
    MeadowCard(Modifier.fillMaxWidth(), tint = if (blocked) MaterialTheme.colorScheme.errorContainer else Garden.colors.cardTint) {
        Text(stringResource(R.string.trouble_notify_title), style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                silentChannels == null -> stringResource(R.string.trouble_notify_off)
                silentChannels.isNotEmpty() -> stringResource(R.string.trouble_notify_silent, silentChannels.joinToString())
                else -> stringResource(R.string.trouble_notify_ok)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (blocked) {
            FilledTonalButton(onClick = onSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.trouble_notify_settings))
            }
        } else {
            FilledTonalButton(
                onClick = {
                    sending = true
                    scope.launch {
                        result = onTest()
                        sending = false
                    }
                },
                enabled = !sending,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(if (sending) R.string.trouble_notify_sending else R.string.trouble_notify_test))
            }
            result?.let {
                Text(
                    when (it) {
                        PushTest.Sent -> stringResource(R.string.trouble_notify_sent)
                        PushTest.NoPhone -> stringResource(R.string.trouble_notify_no_phone)
                        is PushTest.Undelivered -> stringResource(R.string.trouble_notify_undelivered, it.reasons.joinToString())
                        is PushTest.Failed -> it.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == PushTest.Sent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
