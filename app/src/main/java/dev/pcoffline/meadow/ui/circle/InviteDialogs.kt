package dev.pcoffline.meadow.ui.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.InviteLinkPreview
import dev.pcoffline.meadow.data.InviteLinkStatus
import dev.pcoffline.meadow.data.runCatchingCancellable
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.SproutLoader
import dev.pcoffline.meadow.ui.friendlyMessage
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

sealed interface InvitePreviewState {
    data object Loading : InvitePreviewState
    data class Ready(val preview: InviteLinkPreview) : InvitePreviewState
    data class Failed(val error: Throwable) : InvitePreviewState
}

class InvitePreviewViewModel(token: String) : ViewModel() {
    val state: StateFlow<InvitePreviewState> = flow {
        emit(
            runCatchingCancellable { AppGraph.social.previewInviteLink(token) }
                .fold({ InvitePreviewState.Ready(it) }, { InvitePreviewState.Failed(it) }),
        )
    }.stateWhileVisible(this, InvitePreviewState.Loading)
}

/** Shown when someone opens a "Will you be my Bumblebee?" link. */
@Composable
fun AcceptInviteDialog(token: String, onClose: () -> Unit) {
    val viewModel = viewModel(key = "invite-$token") { InvitePreviewViewModel(token) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val context = LocalContext.current

    val preview = (state as? InvitePreviewState.Ready)?.preview
    val canAccept = preview?.status == InviteLinkStatus.OK
    val welcome = preview?.let { stringResource(R.string.accept_invite_done, it.ownerName) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                when {
                    preview == null -> stringResource(R.string.accept_invite_loading_title)
                    else -> stringResource(R.string.accept_invite_title, preview.ownerName)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    InvitePreviewState.Loading -> SproutLoader(Modifier.height(120.dp), label = null)
                    is InvitePreviewState.Failed -> ErrorNote(s.error.friendlyMessage(context))
                    is InvitePreviewState.Ready -> {
                        FlowerAvatar(s.preview.ownerFlower, size = 72.dp)
                        Text(
                            stringResource(
                                when (s.preview.status) {
                                    InviteLinkStatus.OK -> R.string.accept_invite_body
                                    InviteLinkStatus.EXPIRED -> R.string.accept_invite_expired
                                    InviteLinkStatus.USED -> R.string.accept_invite_used
                                    InviteLinkStatus.OWN -> R.string.accept_invite_own
                                    InviteLinkStatus.ALREADY -> R.string.accept_invite_already
                                },
                                s.preview.ownerName,
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            if (canAccept) {
                TextButton(
                    enabled = !action.busy,
                    onClick = {
                        action.run(successMessage = welcome, onSuccess = { celebrate(); onClose() }) {
                            AppGraph.social.acceptInviteLink(token)
                        }
                    },
                ) { Text(stringResource(R.string.accept_invite_yes)) }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (canAccept) TextButton(onClick = onClose) { Text(stringResource(R.string.accept_invite_not_now)) }
        },
    )
}
