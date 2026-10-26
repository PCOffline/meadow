package dev.pcoffline.meadow.ui.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.ui.rememberAction

/**
 * While strict mode is on, Meadow must be safe from battery savers and able to reach
 * notifications. If either is missing, this covers the app until it's fixed or strict mode is
 * turned off. It's the person's own choice; nobody else is told either way.
 */
@Composable
fun StrictGate(strictMode: Boolean, onTroubleshoot: () -> Unit) {
    var state by remember { mutableStateOf(AppGraph.protection.current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = AppGraph.protection.current() }
    if (!strictMode || state.strictReady) return
    val context = LocalContext.current
    val action = rememberAction()
    StrictGateContent(
        state = state,
        busy = action.busy,
        error = action.error,
        onBattery = {
            @Suppress("BatteryLife") // Strict mode can't work while Android may stop Meadow.
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            runCatching { context.startActivity(intent) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        },
        onListener = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
        onTroubleshoot = onTroubleshoot,
        onTurnOff = { action.run { AppGraph.social.setStrictMode(false) } },
    )
}

@Composable
fun StrictGateContent(
    state: ProtectionState,
    busy: Boolean = false,
    error: String? = null,
    onBattery: () -> Unit = {},
    onListener: () -> Unit = {},
    onTroubleshoot: () -> Unit = {},
    onTurnOff: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔒", fontSize = 48.sp)
            Text(
                stringResource(R.string.strict_gate_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.asHeading(),
            )
            Text(
                stringResource(R.string.strict_gate_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.batteryUnrestricted) {
                Button(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_gate_battery)) }
            }
            if (!state.notificationListener) {
                Button(onClick = onListener, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_gate_listener)) }
            }
            OutlinedButton(onClick = onTurnOff, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.strict_gate_turn_off))
            }
            TextButton(onClick = onTroubleshoot) { Text(stringResource(R.string.trouble_button)) }
            ErrorNote(error)
        }
    }
}
