@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * A commitment waiting to be shared: in a Meadow chat with someone in the circle, or through
 * another app. Opened from anywhere ([open]), shown by [ShareCommitmentHost] at the app's root, so
 * it outlives the screen that made the commitment.
 */
object ShareCommitment {
    private val pending = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = pending.asStateFlow()

    fun open(text: String) {
        pending.value = text
    }

    fun close() {
        pending.value = null
    }
}

/** Everyone there's a chat with: Bumblebees first, then the gardens looked after. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetsViewModel : ViewModel() {
    private val profile = AppGraph.sync.profile.mapNotNull { (it as? ProfileState.Ready)?.profile }

    val me: StateFlow<String?> = profile.map { it.uid }.stateWhileVisible(this, null)

    val people: StateFlow<List<UserProfile>?> = profile.distinctUntilChanged { a, b ->
        a.approverIds == b.approverIds && a.approveeIds == b.approveeIds
    }.flatMapLatest { p ->
        combine(AppGraph.social.profiles(p.approverIds), AppGraph.social.profiles(p.approveeIds)) { bees, gardens ->
            (bees + gardens).distinctBy { it.uid }
        }
    }.stateWhileVisible(this, null)
}

@Composable
fun ShareCommitmentHost() {
    val text by ShareCommitment.text.collectAsStateWithLifecycle()
    text?.let { ShareCommitmentSheet(it, onDismiss = ShareCommitment::close) }
}

@Composable
private fun ShareCommitmentSheet(text: String, onDismiss: () -> Unit, viewModel: ShareTargetsViewModel = viewModel { ShareTargetsViewModel() }) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val action = rememberAction()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShareCommitmentContent(
            text = text,
            people = people,
            busy = action.busy,
            error = action.error,
            onSend = { person, sent ->
                val uid = me ?: return@ShareCommitmentContent
                action.run(successMessage = sent, onSuccess = onDismiss) {
                    AppGraph.chats.send(ChatRepository.chatIdFor(uid, person.uid), text)
                }
            },
            onOtherApps = {
                onDismiss()
                context.shareText(text)
            },
        )
    }
}

/**
 * The commitment as it will read, then everyone it can go to in Meadow, then other apps. [people]
 * is null while loading.
 */
@Composable
fun ShareCommitmentContent(
    text: String,
    people: List<UserProfile>?,
    busy: Boolean = false,
    error: String? = null,
    onSend: (person: UserProfile, sentMessage: String) -> Unit,
    onOtherApps: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.share_commitment_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        MeadowCard(Modifier.fillMaxWidth()) { Text(text, style = MaterialTheme.typography.bodyLarge) }
        if (!people.isNullOrEmpty()) {
            Text(stringResource(R.string.share_commitment_in_meadow), style = MaterialTheme.typography.titleSmall)
            people.forEach { person ->
                val sendTo = stringResource(R.string.share_commitment_send_to, person.displayName)
                val sent = stringResource(R.string.share_commitment_sent, person.displayName)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(enabled = !busy, onClickLabel = sendTo, role = Role.Button) { onSend(person, sent) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(person.avatar, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(person.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ErrorNote(error)
        OutlinedButton(onClick = onOtherApps, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.share_commitment_other_apps))
        }
        Spacer(Modifier.padding(bottom = 12.dp))
    }
}
