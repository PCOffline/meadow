package dev.pcoffline.meadow.ui.circle

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.SharedAppUsage
import dev.pcoffline.meadow.data.SharedPage
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.Hint
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.limitText
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.ActionState
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.stats.ProgressCard
import dev.pcoffline.meadow.ui.theme.Garden
import androidx.lifecycle.viewModelScope
import dev.pcoffline.meadow.data.Connectivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** What a Bumblebee can see about a friend they look after. */
class FriendViewModel(uid: String) : ViewModel() {
    // One listener, for the name up top and the page below alike.
    private val profile = AppGraph.social.profile(uid).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val friend: StateFlow<UserProfile?> = profile.stateWhileVisible(this, null)

    /** False until the profile has been read; then [friend] null means they've left Meadow. */
    val loaded: StateFlow<Boolean> = profile.map { true }.stateWhileVisible(this, false)

    // Each part the rules refuse (the friend doesn't share it) reads as not shared.
    private val today = combine(AppGraph.share.minutes(uid, Days.todayKey()), AppGraph.share.apps(uid, Days.todayKey())) { m, a -> m to a }
    val page: StateFlow<SharedPage?> = combine(profile, today, AppGraph.share.progress(uid), AppGraph.share.limits(uid)) { f, (minutes, apps), progress, limits ->
        f?.let { SharedPage(it, minutes, apps, progress, limits) }
    }.stateWhileVisible(this, null)
}

@Composable
fun FriendScreen(
    me: UserProfile,
    uid: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: FriendViewModel = viewModel(key = "friend-$uid") { FriendViewModel(uid) },
) {
    val friend by viewModel.friend.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val online by Connectivity.online.collectAsStateWithLifecycle()
    // Out here: stepping down can hide their profile, and with it the details, before the
    // step down returns; the action has to stay attached to go back.
    val action = rememberAction()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(friend?.displayName.orEmpty(), onBack = onBack)
        val f = friend
        if (f == null && loaded) {
            EmptyState("🍂", stringResource(R.string.friend_gone_title))
        } else if (f == null) {
            Loading()
        } else {
            // Until every part has been read, the page waits rather than saying nothing's shared.
            // Offline, the parts can't be read (what's cached might no longer be shared), and the
            // offline strip already says why.
            val shared = page?.copy(profile = f)
            FriendDetails(
                shared ?: SharedPage(f, null, null, null, null), waiting = shared == null, offline = !online, action, onBack,
                onChat = { onOpenChat(ChatRepository.chatIdFor(me.uid, f.uid)) },
            )
        }
    }
}

@Composable
private fun FriendDetails(
    page: SharedPage,
    waiting: Boolean,
    offline: Boolean,
    action: ActionState,
    onBack: () -> Unit,
    onChat: () -> Unit,
) {
    val f = page.profile
    var confirmStepDown by rememberSaveable { mutableStateOf(false) }
    val steppedDown = stringResource(R.string.friend_stepped_down)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FriendPage(page, waiting, offline) { FilledTonalButton(onClick = onChat) { Text(stringResource(R.string.action_chat)) } }
        ErrorNote(action.error)
        OutlinedButton(onClick = { confirmStepDown = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.friend_step_down, f.displayName))
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmStepDown) {
        AlertDialog(
            onDismissRequest = { confirmStepDown = false },
            title = { Text(stringResource(R.string.friend_step_down_title)) },
            text = { Text(stringResource(R.string.friend_step_down_body, f.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStepDown = false
                    action.run(successMessage = steppedDown, onSuccess = onBack) { AppGraph.social.stepDown(f.uid) }
                }) { Text(stringResource(R.string.friend_step_down_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmStepDown = false }) { Text(stringResource(R.string.friend_step_down_stay)) } },
        )
    }
}

/**
 * A friend's page as their Bumblebees see it: who they are, then each part they share. Settings
 * shows people their own page through this too, so the preview is always the real thing.
 * [action] sits beside the name. While [waiting] for the parts, there's a spinner, unless
 * [offline], where they won't come.
 */
@Composable
fun FriendPage(page: SharedPage, waiting: Boolean = false, offline: Boolean = false, action: @Composable () -> Unit = {}) {
    val f = page.profile
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(f.avatar, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
                if (f.bio.isNotBlank()) Text(f.bio, style = MaterialTheme.typography.bodyMedium)
            }
            action()
        }

        if (waiting) {
            if (!offline) Loading(Modifier.height(140.dp))
            return@Column
        }
        val nothing = page.progress == null && page.minutes == null && page.apps == null && page.limits == null
        if (nothing) Hint(stringResource(R.string.friend_nothing_shared, f.displayName))
        page.progress?.let { ProgressCard(it) }
        if (page.minutes != null || page.apps != null) TodayCard(page.minutes, page.apps.orEmpty(), page.limits.orEmpty())
        page.limits?.takeIf { it.isNotEmpty() }?.let { LimitsCard(it) }
    }
}

@Composable
private fun TodayCard(minutes: Int?, apps: List<SharedAppUsage>, limits: List<AppLimit>) {
    val daily = limits.associate { it.packageName to it.dailyMinutes }
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_today), style = MaterialTheme.typography.titleMedium)
        if (minutes != null) {
            Text(stringResource(R.string.friend_screen_time, minutesText(minutes)), style = MaterialTheme.typography.headlineSmall)
        }
        if (minutes != null && apps.isNotEmpty()) Spacer(Modifier.height(8.dp))
        val max = apps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
        apps.take(8).forEach { app ->
            val limit = daily[app.packageName] ?: 0
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (limit > 0) stringResource(R.string.friend_used_of, minutesText(app.minutes), minutesText(limit))
                            else minutesText(app.minutes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    GrowthBar(
                        if (limit > 0) app.minutes / limit.toFloat() else app.minutes / max.toFloat(),
                        height = 6.dp,
                        color = if (limit > 0) null else Garden.colors.growth,
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitsCard(limits: List<AppLimit>) {
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_limits), style = MaterialTheme.typography.titleMedium)
        limits.forEach { limit ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(limit.packageName, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(limit.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(limitText(limit.dailyMinutes, limit.weeklyMinutes, limit.schedule), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
