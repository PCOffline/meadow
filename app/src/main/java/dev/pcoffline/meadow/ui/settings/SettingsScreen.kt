@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.auth.FlowerPicker
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction

@Composable
fun SettingsScreen(profile: UserProfile, nav: NavController) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var flower by rememberSaveable { mutableStateOf(profile.flower) }
    var confirmStrictOff by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    val action = rememberAction()

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Settings", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeadowCard(Modifier.fillMaxWidth()) {
                Text("You", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                FlowerPicker(flower) { flower = it }
                Button(
                    onClick = { action.run(successMessage = "Saved 🌼") { AppGraph.social.updateProfile(name, flower) } },
                    enabled = !action.busy && name.isNotBlank() && (name != profile.displayName || flower != profile.flower),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Save") }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Keeps Meadow's own settings, uninstalling and the clock out of reach, so limits can't be " +
                                "switched off in a weak moment. Turning it off needs an approver's yes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = profile.strictMode,
                        onCheckedChange = { on ->
                            if (on) action.run(successMessage = "Strict mode on 🌿") { AppGraph.social.setStrictMode(true) }
                            else confirmStrictOff = true
                        },
                    )
                }
            }

            EmergencyBanner()
            MeadowCard(Modifier.fillMaxWidth()) {
                Text("Emergency unlock", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lifts all limits for up to an hour without waiting for an approver. You get " +
                        "${Emergency.ALLOWANCE} every 30 days, and your approvers are told each time, with your reason. " +
                        "Calls and emergency apps are never limited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${profile.emergenciesLeft} of ${Emergency.ALLOWANCE} left",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(
                    onClick = { showEmergency = true },
                    enabled = profile.emergenciesLeft > 0,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Use an emergency unlock") }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text("Protections", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Check usage access, the garden keeper, uninstall protection and notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { nav.navigate("setup") }, modifier = Modifier.padding(top = 8.dp)) { Text("Review protections") }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text("How Meadow keeps its promises", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Tightening a limit applies right away.\n" +
                        "• Loosening or removing one, turning off strict mode, or changing approvers needs an approver's yes.\n" +
                        "• In a real emergency you can lift limits yourself for up to an hour, a few times a month — your approvers are told.\n" +
                        "• When an app's time is up it closes and rests until tomorrow, unless a friend gifts more time.\n" +
                        "• If protections are switched off or your phone goes quiet, your approvers are told.\n" +
                        "• Limits stay on this phone even when you're offline or signed out.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            ErrorNote(action.error)
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEmergency) EmergencyDialog(packageName = null, appName = null, onDismiss = { showEmergency = false })
    if (confirmStrictOff) {
        AlertDialog(
            onDismissRequest = { confirmStrictOff = false },
            title = { Text("Turn off strict mode?") },
            text = {
                Text(
                    if (profile.approverIds.isEmpty()) "Strict mode will turn off right away."
                    else "Your approvers will be asked first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStrictOff = false
                    action.run {
                        val outcome = AppGraph.social.setStrictMode(false)
                        action.toast(if (outcome == ChangeOutcome.REQUESTED) "Asked your approvers 💌" else "Strict mode off")
                    }
                }) { Text(if (profile.approverIds.isEmpty()) "Turn off" else "Ask") }
            },
            dismissButton = { TextButton(onClick = { confirmStrictOff = false }) { Text("Keep it on") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your limits keep working on this phone while you're signed out, but you won't be able to ask for more time.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; action.run { AppGraph.auth.signOut() } }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay") } },
        )
    }
}
