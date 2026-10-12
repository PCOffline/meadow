@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.setup

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.enforcement.GuardianService
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.GrowthBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.size
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.theme.Garden

@Composable
fun SetupScreen(onDone: () -> Unit, onTroubleshoot: () -> Unit = {}) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var state by remember { mutableStateOf(AppGraph.protection.current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = AppGraph.protection.current()
        if (state.essentialsReady) GuardianService.start(context)
        AppGraph.protection.check(state)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = AppGraph.protection.current()
    }
    // Prominent disclosure: explain what each sensitive permission is used for before asking.
    var disclosure by remember { mutableStateOf<Disclosure?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val steps = listOf(
            state.usageAccess, state.accessibility, state.notificationListener, state.notifications,
            state.overlay, state.deviceAdmin, state.batteryUnrestricted,
        )
        val done = steps.count { it }
        val total = steps.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.asHeading())
                Text(
                    stringResource(R.string.setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.setup_progress, done, total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                GrowthBar(done / total.toFloat(), color = Garden.colors.leaf)
            }
            Spacer(Modifier.width(12.dp))
            BloomFlower(0.1f + 0.9f * done / total, Garden.colors.petalPink, Modifier.size(76.dp, 116.dp), petals = 7)
        }
        SetupStep(
            emoji = "📊",
            title = R.string.setup_usage_title,
            body = R.string.setup_usage_body,
            done = state.usageAccess,
        ) { disclosure = Disclosure.USAGE }
        SetupStep(
            emoji = "🌿",
            title = R.string.setup_keeper_title,
            body = R.string.setup_keeper_body,
            done = state.accessibility,
        ) { disclosure = Disclosure.KEEPER }
        SetupStep(
            emoji = "🔕",
            title = R.string.setup_listener_title,
            body = R.string.setup_listener_body,
            done = state.notificationListener,
        ) { disclosure = Disclosure.LISTENER }
        SetupStep(
            emoji = "🔔",
            title = R.string.setup_notifications_title,
            body = R.string.setup_notifications_body,
            done = state.notifications,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, withExtraPackage = true)
            }
        }
        SetupStep(
            emoji = "🪟",
            title = R.string.setup_overlay_title,
            body = R.string.setup_overlay_body,
            done = state.overlay,
        ) { context.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackageUri = true) }
        SetupStep(
            emoji = "🛡️",
            title = R.string.setup_admin_title,
            body = R.string.setup_admin_body,
            done = state.deviceAdmin,
        ) {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AppGraph.protection.adminComponent())
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        resources.getString(R.string.setup_admin_explanation),
                    ),
            )
        }
        SetupStep(
            emoji = "🔋",
            title = R.string.setup_battery_title,
            body = R.string.setup_battery_body,
            done = state.batteryUnrestricted,
        ) {
            @Suppress("BatteryLife")
            context.openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackageUri = true)
        }
        TextButton(onClick = onTroubleshoot, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.trouble_open_from_setup))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            enabled = state.essentialsReady,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(if (state.essentialsReady) R.string.setup_done else R.string.setup_needs_essentials)) }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.setup_later))
        }
    }
    disclosure?.let { d ->
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(d.body)) },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = null
                    context.openSettings(d.settingsAction)
                }) { Text(stringResource(R.string.disclosure_agree)) }
            },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text(stringResource(R.string.disclosure_not_now)) } },
        )
    }
}

private enum class Disclosure(@StringRes val body: Int, val settingsAction: String) {
    USAGE(R.string.disclosure_usage, Settings.ACTION_USAGE_ACCESS_SETTINGS),
    KEEPER(R.string.disclosure_keeper, Settings.ACTION_ACCESSIBILITY_SETTINGS),
    LISTENER(R.string.disclosure_listener, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
}

@Composable
private fun SetupStep(emoji: String, @StringRes title: Int, @StringRes body: Int, done: Boolean, onClick: () -> Unit) {
    MeadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AnimatedVisibility(done, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn()) {
                        Pill(stringResource(R.string.setup_step_done), MaterialTheme.colorScheme.primaryContainer)
                    }
                }
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!done) {
                    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.setup_step_allow)) }
                }
            }
        }
    }
}

private fun Context.openSettings(action: String, withPackageUri: Boolean = false, withExtraPackage: Boolean = false) {
    val intent = Intent(action)
    if (withPackageUri) intent.data = Uri.parse("package:$packageName")
    if (withExtraPackage) intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }.onFailure {
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

/** Shown when the app was built without a Firebase configuration. */
@Composable
fun BackendMissingScreen() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌱", fontSize = 56.sp)
        Text(stringResource(R.string.backend_missing_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        MeadowCard(Modifier.fillMaxWidth(), tint = Garden.colors.cardTint) {
            Text(
                stringResource(R.string.backend_missing_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

