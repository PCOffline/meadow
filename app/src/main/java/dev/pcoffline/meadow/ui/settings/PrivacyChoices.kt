package dev.pcoffline.meadow.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The privacy choices as made on this screen. A switch moves the moment it's touched; the
 * choices are saved once they've stopped changing for a moment, all in one write. Once that's
 * done, or has failed, [current] is the saved choices again.
 */
@Stable
internal class PrivacyChoices(private val saved: State<Privacy>) {
    /** Choices made here and not saved yet; null while the saved ones are what's shown. */
    var unsaved by mutableStateOf<Privacy?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        internal set

    val current: Privacy get() = unsaved ?: saved.value

    fun change(to: Privacy) {
        error = null
        unsaved = to
    }

    /** Called once [choice] has been saved, or failed to: the saved choices take over again. */
    internal fun settled(choice: Privacy) {
        if (unsaved == choice) unsaved = null
    }
}

@Composable
internal fun rememberPrivacyChoices(
    saved: Privacy,
    save: suspend (Privacy) -> Unit = { AppGraph.profiles.setPrivacy(it) },
): PrivacyChoices {
    val savedState = rememberUpdatedState(saved)
    val choices = remember { PrivacyChoices(savedState) }
    val context = LocalContext.current.applicationContext
    val snackbar = LocalSnackbar.current
    val savedMessage = stringResource(R.string.settings_privacy_saved)
    val saveNow by rememberUpdatedState(save)
    LaunchedEffect(choices.unsaved) {
        val choice = choices.unsaved ?: return@LaunchedEffect
        // Another switch touched within the pause restarts it, so a run of changes is one save.
        delay(PRIVACY_SETTLE_MS)
        if (choice == savedState.value) {
            choices.settled(choice)
            return@LaunchedEffect
        }
        // In the app's scope: leaving the screen straight after a change still saves it.
        AppGraph.scope.launch(Dispatchers.Main.immediate) {
            try {
                saveNow(choice)
                launch { snackbar.showSnackbar(savedMessage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Meadow", "Privacy not saved", e)
                choices.error = e.friendlyMessage(context)
            } finally {
                choices.settled(choice)
            }
        }
    }
    return choices
}

/** How long the choices have to stay put before they're saved. */
internal const val PRIVACY_SETTLE_MS = 700L

/** The privacy choices, part by part; with [onPreview], a way to see the page they shape (see PrivacyPreviewContent). */
@Composable
internal fun PrivacyCard(choices: PrivacyChoices, onPreview: (() -> Unit)? = null) {
    val p = choices.current
    // Each change starts from what's showing now, so quick taps on two switches both count.
    fun change(edit: Privacy.() -> Privacy) = choices.change(choices.current.edit())
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
        SwitchRow(stringResource(R.string.settings_share_limits), checked = p.shareLimits) { on -> change { copy(shareLimits = on) } }
        SwitchRow(stringResource(R.string.settings_share_time), checked = p.shareTime) { on -> change { copy(shareTime = on) } }
        SwitchRow(stringResource(R.string.settings_share_apps), checked = p.shareApps) { on -> change { copy(shareApps = on) } }
        SwitchRow(stringResource(R.string.settings_share_progress), checked = p.shareProgress) { on -> change { copy(shareProgress = on) } }
        ErrorNote(choices.error)
        if (onPreview != null) {
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_privacy_preview))
            }
        }
    }
}
