package dev.pcoffline.meadow.ui.setup

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.enforcement.PhoneMaker
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.limits.BackTopBar

/** Steps that keep Android, and the phone maker's own battery savers, from putting Meadow to sleep. */
@Composable
fun TroubleshootScreen(onBack: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppGraph.protection.current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = AppGraph.protection.current() }
    val brand = remember { PhoneMaker.brand() }
    val steps = remember { PhoneMaker.steps(context, brand) }
    TroubleshootContent(
        state = state,
        steps = steps,
        onBack = onBack,
        onOpenSetup = onOpenSetup,
        onOpen = { PhoneMaker.open(context, it) },
        onMoreHelp = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PhoneMaker.helpUrl(brand))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
}

/** The troubleshooting steps, drawn from plain state (also used for previews and tests). */
@Composable
fun TroubleshootContent(
    state: ProtectionState,
    steps: List<PhoneMaker.Step>,
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
            Text(
                stringResource(R.string.trouble_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.essentialsReady) {
                MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.trouble_essentials), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_protections_button))
                    }
                }
            }
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
