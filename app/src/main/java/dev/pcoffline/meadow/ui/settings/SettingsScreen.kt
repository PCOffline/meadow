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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.auth.FlowerPicker
import dev.pcoffline.meadow.ui.auth.SecureAccountCard
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction

@Composable
fun SettingsScreen(profile: UserProfile, onBack: () -> Unit, onOpenSetup: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var flower by rememberSaveable { mutableStateOf(profile.flower) }
    var confirmStrictOff by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    val anonymous by AppGraph.auth.anonymous.collectAsStateWithLifecycle()
    val action = rememberAction()
    val hasBees = profile.approverIds.isNotEmpty()
    val saved = stringResource(R.string.settings_saved)
    val strictOn = stringResource(R.string.settings_strict_on)
    val strictOff = stringResource(R.string.settings_strict_off_done)
    val asked = stringResource(R.string.settings_strict_asked)

    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.action_settings), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_you), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.onboarding_name_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                FlowerPicker(flower) { flower = it }
                Button(
                    onClick = { action.run(successMessage = saved) { AppGraph.social.updateProfile(name, flower) } },
                    enabled = !action.busy && name.isNotBlank() && (name != profile.displayName || flower != profile.flower),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }

            if (anonymous) SecureAccountCard(R.string.settings_secure_reason)

            MeadowCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_strict_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_strict_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = profile.strictMode,
                        onCheckedChange = { on ->
                            if (on) action.run(successMessage = strictOn) { AppGraph.social.setStrictMode(true) } else confirmStrictOff = true
                        },
                    )
                }
            }

            EmergencyBanner()
            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.settings_emergency_body, Emergency.ALLOWANCE, Emergency.ALLOWANCE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_emergency_left, profile.emergenciesLeft, Emergency.ALLOWANCE),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(
                    onClick = { showEmergency = true },
                    enabled = profile.emergenciesLeft > 0,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.settings_emergency_button)) }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_protections_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_protections_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_protections_button))
                }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_promises_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_promises_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            ErrorNote(action.error)
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_sign_out))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEmergency) EmergencyDialog(packageName = null, appName = null, onDismiss = { showEmergency = false })
    if (confirmStrictOff) {
        AlertDialog(
            onDismissRequest = { confirmStrictOff = false },
            title = { Text(stringResource(R.string.settings_strict_off_title)) },
            text = { Text(stringResource(if (hasBees) R.string.settings_strict_off_ask else R.string.settings_strict_off_now)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStrictOff = false
                    action.run {
                        val outcome = AppGraph.social.setStrictMode(false)
                        action.toast(if (outcome == ChangeOutcome.REQUESTED) asked else strictOff)
                    }
                }) { Text(stringResource(if (hasBees) R.string.settings_strict_off_ask_button else R.string.settings_strict_off_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmStrictOff = false }) { Text(stringResource(R.string.settings_strict_keep)) } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(if (anonymous) R.string.settings_sign_out_anonymous else R.string.settings_sign_out_body)) },
            confirmButton = {
                if (anonymous) {
                    TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_ok)) }
                } else {
                    TextButton(onClick = {
                        confirmSignOut = false
                        action.run { AppGraph.auth.signOut() }
                    }) { Text(stringResource(R.string.settings_sign_out)) }
                }
            },
            dismissButton = {
                if (!anonymous) TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.settings_sign_out_stay)) }
            },
        )
    }
}
