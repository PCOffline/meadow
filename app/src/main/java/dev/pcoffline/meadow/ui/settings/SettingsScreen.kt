package dev.pcoffline.meadow.ui.settings

import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.auth.SecureAccountCard
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import dev.pcoffline.meadow.ui.legal.LegalDoc
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit,
) {
    var confirmStrictOff by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    val anonymous by AppGraph.auth.anonymous.collectAsStateWithLifecycle()
    val action = rememberAction()
    val hasBees = profile.approverIds.isNotEmpty()
    val strictOn = stringResource(R.string.settings_strict_on)
    val strictOff = stringResource(R.string.settings_strict_off_done)
    val asked = stringResource(R.string.settings_strict_asked)

    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.action_settings), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileCard(profile, onOpenProfile)

            if (anonymous) SecureAccountCard(R.string.settings_secure_reason)

            MeadowCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = stringResource(R.string.settings_strict_title),
                    body = stringResource(R.string.settings_strict_body),
                    checked = profile.strictMode,
                    onChange = { on ->
                        if (on) action.run(successMessage = strictOn) { AppGraph.social.setStrictMode(true) } else confirmStrictOff = true
                    },
                )
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

            PrivacyCard(profile)
            UpdatesCard()

            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_promises_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_promises_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            DataCard(onOpenLegal = onOpenLegal, onDelete = { confirmDelete = true })

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
    if (confirmDelete) DeleteAccountDialog(onDismiss = { confirmDelete = false })
}

@Composable
private fun ProfileCard(profile: UserProfile, onOpenProfile: () -> Unit) {
    MeadowCard(Modifier.fillMaxWidth(), onClick = onOpenProfile) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(profile.avatar, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    profile.bio.ifBlank { stringResource(R.string.profile_open) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.profile_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyCard(profile: UserProfile) {
    val action = rememberAction()
    val saved = stringResource(R.string.settings_privacy_saved)
    val privacy = profile.privacy
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SwitchRow(
            title = stringResource(R.string.settings_share_usage),
            body = stringResource(R.string.settings_share_usage_body),
            checked = privacy.shareUsage,
            enabled = !action.busy,
            onChange = { on -> action.run(successMessage = saved) { AppGraph.profiles.setPrivacy(privacy.copy(shareUsage = on)) } },
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.settings_share_protection),
            body = stringResource(R.string.settings_share_protection_body),
            checked = privacy.shareProtection,
            enabled = !action.busy,
            onChange = { on -> action.run(successMessage = saved) { AppGraph.profiles.setPrivacy(privacy.copy(shareProtection = on)) } },
        )
        ErrorNote(action.error)
    }
}

@Composable
private fun UpdatesCard() {
    val context = LocalContext.current
    val updater = AppGraph.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val auto by AppGraph.store.autoUpdate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var needsPermission by remember { mutableStateOf(false) }

    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_updates_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_updates_version, updater.currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SwitchRow(
            title = stringResource(R.string.settings_updates_auto),
            body = stringResource(R.string.settings_updates_auto_body),
            checked = auto,
            onChange = AppGraph.store::setAutoUpdate,
        )
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Status(stringResource(R.string.settings_updates_checking))
            UpdateState.UpToDate -> Status(stringResource(R.string.settings_updates_current))
            is UpdateState.Available -> {
                Text(stringResource(R.string.settings_updates_available, s.update.version.toString()), style = MaterialTheme.typography.titleSmall)
                if (s.update.notes.isNotBlank()) {
                    Text(
                        s.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (needsPermission && !updater.canInstall()) {
                    Status(stringResource(R.string.settings_updates_allow))
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(updater.installPermissionIntent())
                        } catch (_: ActivityNotFoundException) {
                            needsPermission = false
                        }
                    }) { Text(stringResource(R.string.settings_updates_allow_button)) }
                }
                Button(onClick = {
                    if (updater.canInstall()) scope.launch { updater.install(s.update) } else needsPermission = true
                }) { Text(stringResource(R.string.settings_updates_install)) }
            }
            is UpdateState.Downloading -> {
                Status(stringResource(R.string.settings_updates_downloading, (s.progress * 100).toInt()))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            is UpdateState.Installing -> Status(stringResource(R.string.settings_updates_installing))
            is UpdateState.Failed -> ErrorNote(s.message)
        }
        val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
        if (state !is UpdateState.Available) {
            OutlinedButton(onClick = { scope.launch { updater.check() } }, enabled = !busy, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.settings_updates_check))
            }
        }
    }
}

@Composable
private fun DataCard(onOpenLegal: (LegalDoc) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val action = rememberAction()
    val exported = stringResource(R.string.settings_export_done)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        action.run(successMessage = exported) {
            val json = AppGraph.profiles.exportData()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    }
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_legal_title), style = MaterialTheme.typography.titleMedium)
        LegalDoc.entries.forEach { doc ->
            LinkRow(stringResource(doc.title)) { onOpenLegal(doc) }
        }
        LinkRow(stringResource(R.string.settings_export), enabled = !action.busy) { save.launch("meadow-data.json") }
        LinkRow(stringResource(R.string.settings_delete), danger = true, onClick = onDelete)
        ErrorNote(action.error)
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val action = rememberAction()
    val word = stringResource(R.string.settings_delete_confirm_word)
    AlertDialog(
        onDismissRequest = { if (!action.busy) onDismiss() },
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_delete_body))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(20) },
                    label = { Text(stringResource(R.string.settings_delete_confirm_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { action.run { AppGraph.profiles.deleteAccount() } },
                enabled = !action.busy && typed.trim().equals(word, ignoreCase = true),
            ) { Text(stringResource(R.string.settings_delete_button), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !action.busy) { Text(stringResource(R.string.settings_delete_keep)) } },
    )
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun Status(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp))
}
