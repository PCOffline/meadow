@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.setup

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.enforcement.GuardianService
import dev.pcoffline.meadow.enforcement.MeadowNotificationListener
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.auth.SecureAccountForm
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.ui.theme.Garden

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    onTroubleshoot: () -> Unit = {},
    read: () -> ProtectionState = { AppGraph.protection.current() },
    anonymous: Boolean = AppGraph.auth.anonymous.collectAsStateWithLifecycle().value,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var state by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = read()
        if (state.essentialsReady) GuardianService.start(context)
        AppGraph.protection.check(state)
    }
    val activity = LocalActivity.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state = read()
        val canAskAgain = activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        context.afterNotificationRequest(granted, canAskAgain)
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
                Text(pluralStringResource(R.plurals.setup_progress, done, done, total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                GrowthBar(done / total.toFloat(), color = Garden.colors.growth)
            }
            Spacer(Modifier.width(12.dp))
            BloomFlower(0.1f + 0.9f * done / total, Garden.colors.petalPink, Modifier.size(76.dp, 116.dp), petals = 7)
        }
        SetupStep(
            emoji = "📊",
            title = R.string.setup_usage_title,
            body = R.string.setup_usage_body,
            how = R.string.setup_usage_how,
            done = state.usageAccess,
            required = true,
        ) { disclosure = Disclosure.USAGE }
        SetupStep(
            emoji = "🌿",
            title = R.string.setup_keeper_title,
            body = R.string.setup_keeper_body,
            how = R.string.setup_keeper_how,
            done = state.accessibility,
            required = true,
            restricted = true,
        ) { disclosure = Disclosure.KEEPER }
        SetupStep(
            emoji = "🔕",
            title = R.string.setup_listener_title,
            body = R.string.setup_listener_body,
            how = R.string.setup_listener_how,
            done = state.notificationListener,
            required = true,
            restricted = true,
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
            how = R.string.setup_overlay_how,
            done = state.overlay,
        ) { context.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackageUri = true) }
        SetupStep(
            emoji = "🛡️",
            title = R.string.setup_admin_title,
            body = R.string.setup_admin_body,
            how = R.string.setup_admin_how,
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
        // Not a permission, so not counted above, but a garden without an email is lost with the phone.
        var addingEmail by rememberSaveable { mutableStateOf(false) }
        SetupStep(
            emoji = "🔐",
            title = R.string.setup_account_title,
            body = R.string.setup_account_body,
            done = !anonymous,
            action = R.string.setup_account_action,
            expanded = if (addingEmail) {
                { SecureAccountForm() }
            } else {
                null
            },
        ) { addingEmail = true }
        TextButton(onClick = onTroubleshoot, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.trouble_button))
        }
        Spacer(Modifier.height(8.dp))
        // One way out: into the garden once the essentials are on, or later until then.
        if (state.essentialsReady) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.setup_done)) }
        } else {
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.setup_later))
            }
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
                    context.openFirst(d.intents(context))
                }) { Text(stringResource(R.string.disclosure_agree)) }
            },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text(stringResource(R.string.disclosure_not_now)) } },
        )
    }
}

internal enum class Disclosure(@StringRes val body: Int) {
    USAGE(R.string.disclosure_usage),
    KEEPER(R.string.disclosure_keeper),
    LISTENER(R.string.disclosure_listener);

    /**
     * Where to switch it on, best first: Meadow's own page where Android has one, so there's no
     * hunting through a list, then the list itself.
     */
    fun intents(context: Context): List<Intent> = when (this) {
        USAGE -> listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        // Android keeps the page for one accessibility service to itself (a system-only
        // permission), so the list is as close as an app can get.
        KEEPER -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        LISTENER -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MeadowNotificationListener::class.java).flattenToString(),
                )
            } else {
                null
            },
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }
}

/**
 * [required] steps are the ones limits can't work without. [how] says where to tap in Android's
 * settings. [restricted] steps are ones Android greys out for apps installed outside an app store
 * until "Allow restricted settings" is chosen in the app's info (Android 13 and later).
 */
@Composable
private fun SetupStep(
    emoji: String,
    @StringRes title: Int,
    @StringRes body: Int,
    done: Boolean,
    @StringRes how: Int? = null,
    required: Boolean = false,
    restricted: Boolean = false,
    @StringRes action: Int = R.string.setup_step_allow,
    expanded: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    MeadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (required && !done) Pill(stringResource(R.string.setup_step_required), MaterialTheme.colorScheme.secondaryContainer)
                    AnimatedVisibility(done, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn()) {
                        Pill(stringResource(R.string.setup_step_done), MaterialTheme.colorScheme.primaryContainer)
                    }
                }
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!done && expanded != null) {
                    Spacer(Modifier.height(4.dp))
                    expanded()
                } else if (!done) {
                    // Exactly where to tap on the screen the button opens, which differs between phones.
                    how?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(action)) }
                    if (restricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            stringResource(R.string.setup_restricted_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        TextButton(onClick = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackageUri = true) }) {
                            Text(stringResource(R.string.setup_restricted_action))
                        }
                    }
                }
            }
        }
    }
}

/**
 * After asking to post notifications: once someone has refused for good, Android answers no
 * without showing its dialog, so the step would do nothing. Opens the app's notification settings
 * instead, where they can still be turned on.
 */
internal fun Context.afterNotificationRequest(granted: Boolean, canAskAgain: Boolean) {
    if (!granted && !canAskAgain) openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, withExtraPackage = true)
}

private fun Context.openSettings(action: String, withPackageUri: Boolean = false, withExtraPackage: Boolean = false) {
    val intent = Intent(action)
    if (withPackageUri) intent.data = "package:$packageName".toUri()
    if (withExtraPackage) intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    openFirst(listOf(intent))
}

/** Opens the first of [intents] the phone can open, or else Settings itself. */
internal fun Context.openFirst(intents: List<Intent>) {
    for (intent in intents + Intent(Settings.ACTION_SETTINGS)) {
        if (runCatching { startActivity(intent) }.isSuccess) return
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

