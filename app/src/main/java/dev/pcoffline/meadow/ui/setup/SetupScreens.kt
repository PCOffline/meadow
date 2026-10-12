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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pcoffline.meadow.AppGraph
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
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppGraph.protection.current()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = AppGraph.protection.current()
        if (state.essentialsReady) GuardianService.start(context)
        AppGraph.protection.check(state)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = AppGraph.protection.current()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val done = listOf(state.usageAccess, state.accessibility, state.notifications, state.overlay, state.deviceAdmin, state.batteryUnrestricted)
            .count { it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Prepare the soil 🌱", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "A few permissions let Meadow keep your promises for you. Your flower blooms as you go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("$done of 6 ready", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                GrowthBar(done / 6f, color = Garden.colors.leaf)
            }
            Spacer(Modifier.width(12.dp))
            BloomFlower(0.1f + 0.9f * done / 6f, Garden.colors.petalPink, Modifier.size(76.dp, 116.dp), petals = 7)
        }
        SetupStep(
            emoji = "📊",
            title = "Usage access",
            body = "Lets Meadow measure how long each app is open. Required.",
            done = state.usageAccess,
        ) { context.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
        SetupStep(
            emoji = "🌿",
            title = "Garden keeper",
            body = "An accessibility service that notices which app is on screen and gently closes it when its time is up. " +
                "It never reads what you type and nothing on your screen leaves your phone. Required.\n\n" +
                "If the switch is greyed out: open Meadow's App info → ⋮ → \"Allow restricted settings\", then try again.",
            done = state.accessibility,
        ) { context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        SetupStep(
            emoji = "🔔",
            title = "Notifications",
            body = "Reminders before time runs out, answers from friends, and messages.",
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
            title = "Show over other apps",
            body = "A backup so the rest screen can still appear if the garden keeper is ever paused.",
            done = state.overlay,
        ) { context.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackageUri = true) }
        SetupStep(
            emoji = "🛡️",
            title = "Uninstall protection",
            body = "Makes uninstalling Meadow a deliberate step. If it's ever turned off, your Petal Pals hear about it.",
            done = state.deviceAdmin,
        ) {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AppGraph.protection.adminComponent())
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Keeps Meadow from being removed on a whim. Your Petal Pals are told if this is switched off.",
                    ),
            )
        }
        SetupStep(
            emoji = "🔋",
            title = "Run in the background",
            body = "Stops the battery saver from putting the garden keeper to sleep.",
            done = state.batteryUnrestricted,
        ) {
            @Suppress("BatteryLife")
            context.openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackageUri = true)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            enabled = state.essentialsReady,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (state.essentialsReady) "Into the garden 🌼" else "Usage access and garden keeper are needed") }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("I'll do this later")
        }
    }
}

@Composable
private fun SetupStep(emoji: String, title: String, body: String, done: Boolean, onClick: () -> Unit) {
    MeadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AnimatedVisibility(done, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn()) {
                        Pill("Done ✓", MaterialTheme.colorScheme.primaryContainer)
                    }
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!done) {
                    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text("Allow") }
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
        Text("The garden needs its soil", style = MaterialTheme.typography.headlineSmall)
        MeadowCard(Modifier.fillMaxWidth(), tint = Garden.colors.cardTint) {
            Text(
                "This build of Meadow isn't connected to a backend yet. Add your Firebase project's " +
                    "google-services.json to the app/ folder and deploy the backend in /backend, then rebuild. " +
                    "The README walks through it step by step.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

