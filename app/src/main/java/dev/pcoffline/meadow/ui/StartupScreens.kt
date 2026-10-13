package dev.pcoffline.meadow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.SproutLoader
import kotlinx.coroutines.delay

/** How long the first load may take before the person is told and offered another try. */
private const val SLOW_AFTER_MS = 15_000L

/** The garden's first load. If the server is slow to answer, says so and offers another try. */
@Composable
internal fun StartupLoading() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_AFTER_MS)
        slow = true
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SproutLoader(Modifier.height(120.dp))
        if (slow) {
            Text(
                stringResource(R.string.startup_slow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { AppGraph.sync.retry() }) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

/** The server wouldn't send the garden. Says why in plain words, with a way to try again. */
@Composable
internal fun GardenUnavailable(cause: Throwable) {
    val context = LocalContext.current
    val anonymous by AppGraph.auth.anonymous.collectAsStateWithLifecycle()
    val action = rememberAction()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            emoji = "🥀",
            title = stringResource(R.string.startup_unavailable_title),
            body = cause.friendlyMessage(context),
        ) {
            Button(onClick = { AppGraph.sync.retry() }) { Text(stringResource(R.string.action_try_again)) }
        }
        // A guest garden can't be signed back into, so it's only offered with an account.
        if (!anonymous) {
            TextButton(onClick = { action.run { AppGraph.auth.signOut() } }, enabled = !action.busy) {
                Text(stringResource(R.string.profile_setup_other_account))
            }
        }
        ErrorNote(action.error)
    }
}
