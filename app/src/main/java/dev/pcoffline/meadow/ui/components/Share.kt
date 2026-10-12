@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.ui.rememberAction

/** Opens Android's share sheet with [text]. */
fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Sharing is always the person's own choice: [text] goes only where they send it, to another
 * app or as a message to the people in their circle they pick.
 */
@Composable
fun ShareDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val me = (state as? ProfileState.Ready)?.profile
    val circle = remember(me) { me?.let { (it.approverIds + it.approveeIds).distinct() }.orEmpty() }
    val people by remember(circle) { AppGraph.social.profiles(circle) }.collectAsStateWithLifecycle(emptyList())
    var chosen by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val action = rememberAction()
    val sent = stringResource(R.string.share_sent)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MeadowCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = { context.shareText(text) }) { Text(stringResource(R.string.share_elsewhere)) }
                if (people.isNotEmpty()) {
                    Text(stringResource(R.string.share_to_circle), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEach { p ->
                            val on = p.uid in chosen
                            FilterChip(
                                selected = on,
                                onClick = { chosen = if (on) chosen - p.uid else chosen + p.uid },
                                label = { Text("${p.avatar.emoji} ${p.displayName}") },
                            )
                        }
                    }
                }
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            if (people.isNotEmpty()) {
                TextButton(
                    enabled = chosen.isNotEmpty() && !action.busy && me != null,
                    onClick = {
                        val uid = me?.uid ?: return@TextButton
                        action.run(successMessage = sent, onSuccess = onDismiss) {
                            chosen.forEach { AppGraph.chats.send(ChatRepository.chatIdFor(uid, it), text) }
                        }
                    },
                ) { Text(stringResource(R.string.share_send)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
