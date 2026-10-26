package dev.pcoffline.meadow.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.SharedUsage
import dev.pcoffline.meadow.data.Sharing
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class PrivacyPreviewViewModel : ViewModel() {
    /** Today's summary as it would be sent now, read again each minute while it's on screen. */
    val today: StateFlow<SharedUsage?> = flow<SharedUsage?> {
        while (true) {
            emit(SyncWorker.todaySummary())
            delay(REFRESH_MS)
        }
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, null)

    private companion object {
        const val REFRESH_MS = 60_000L
    }
}

@Composable
fun PrivacyPreviewScreen(profile: UserProfile, onBack: () -> Unit, viewModel: PrivacyPreviewViewModel = viewModel { PrivacyPreviewViewModel() }) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    PrivacyPreviewContent(profile, today, limits, onBack)
}

/**
 * The privacy choices, with the person's own page below exactly as their Bumblebees see it, so each
 * choice shows what it does as it's made. Drawn from plain state (also used for tests).
 */
@Composable
fun PrivacyPreviewContent(profile: UserProfile, today: SharedUsage?, limits: List<AppLimit>, onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.privacy_preview_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PrivacyCard(profile)
            Text(
                stringResource(R.string.privacy_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Framed, so it reads as a look at another screen rather than part of this one.
            val frame = RoundedCornerShape(28.dp)
            Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, frame).padding(16.dp)) {
                // Until today's time is read, a page that shares it waits rather than showing a made-up zero.
                if (today == null && profile.privacy.shareUsage) {
                    Loading(Modifier.height(140.dp))
                } else {
                    val page = Sharing.page(profile, { checkNotNull(today) }, limits)
                    FriendPage(page.profile, page.usage, page.limits)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
