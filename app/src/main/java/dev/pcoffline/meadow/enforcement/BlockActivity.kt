@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.pcoffline.meadow.enforcement

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Sites
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.FallingPetals
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PetalBurst
import dev.pcoffline.meadow.ui.components.SkyMood
import dev.pcoffline.meadow.ui.components.SkyScene
import dev.pcoffline.meadow.ui.components.SleepyZs
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.opensAtText
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.MeadowAppTheme
import dev.pcoffline.meadow.ui.ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The calm screen shown in place of an app whose time is up. */
class BlockActivity : ComponentActivity() {
    private val packageState = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        packageState.value = intent.getStringExtra(EXTRA_PACKAGE)
        // Not tied to the lifecycle: one that comes and goes left back to the system at times, which
        // shrank the screen away into the app it covers.
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            MeadowAppTheme {
                val pkg by packageState.collectAsStateWithLifecycle()
                pkg?.let { RestScreen(it, onHome = ::goHome, onOpen = ::openApp) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Kept as the screen's own, so Open reads the browser it now names.
        setIntent(intent)
        packageState.value = intent.getStringExtra(EXTRA_PACKAGE)
    }

    override fun onResume() {
        super.onResume()
        packageState.value?.let {
            markShown(it)
            showing = it
        }
    }

    override fun onPause() {
        showing = null
        super.onPause()
    }

    /** Home, fading out the same way however it's asked for, instead of the launcher's zoom. */
    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home, ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
        finish()
        fadeOut()
    }

    @Suppress("DEPRECATION")
    private fun fadeOut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    /** Opens the app again, or a website in the browser it was open in (or any, if that's gone). */
    private fun openApp(pkg: String) {
        val browser = intent.getStringExtra(EXTRA_BROWSER)
        val opened = openIntents(pkg, browser).any { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
        if (!opened) Log.w(TAG, "Nothing could open what was resting")
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        /** For a website: the browser it was open in, to open it there again. */
        const val EXTRA_BROWSER = "browser"
        private const val TAG = "BlockActivity"

        /** What opens [pkg] again, best first: a website in [browser], then in any browser; an app itself. */
        internal fun Context.openIntents(pkg: String, browser: String?): List<Intent> {
            val domain = Sites.domainOf(pkg) ?: return listOfNotNull(packageManager.getLaunchIntentForPackage(pkg))
            val site = Intent(Intent.ACTION_VIEW, "https://$domain".toUri())
            return listOfNotNull(browser?.let { Intent(site).setPackage(it) }, site)
        }

        /** The app the screen last showed for, and when (elapsed realtime). */
        @Volatile private var lastShown: Pair<String, Long>? = null

        /** The app the screen is on screen for right now, if it is. */
        @Volatile internal var showing: String? = null

        /** Whether the screen is on screen for [packageName] right now. */
        fun isShowingFor(packageName: String): Boolean = showing == packageName

        internal fun markShown(packageName: String) {
            lastShown = packageName to SystemClock.elapsedRealtime()
        }

        /** Whether the screen has shown for [packageName] since [since] (elapsed realtime). */
        fun shownSince(packageName: String, since: Long): Boolean =
            lastShown?.let { (pkg, at) -> pkg == packageName && at >= since } == true

        fun launch(context: Context, packageName: String, browser: String? = null) {
            val intent = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_BROWSER, browser)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // Can't happen for our own activity; guarded so enforcement never crashes the service.
            }
        }
    }
}

data class RestState(
    val appName: String = "",
    val budget: AppBudget? = null,
    val signedIn: Boolean = false,
    val hasBumblebees: Boolean = false,
    val pendingRequest: LimitRequest? = null,
) {
    val unlocked: Boolean get() = budget != null && !budget.exhausted
}

class RestViewModel(private val packageName: String) : ViewModel() {
    private val pending = AppGraph.sync.profile.flatMapLatest { state ->
        val uid = (state as? ProfileState.Ready)?.profile?.uid ?: return@flatMapLatest flowOf(null)
        AppGraph.requests.outgoing(uid).map { requests ->
            requests.firstOrNull {
                it.type == RequestType.EXTRA_TIME && it.packageName == packageName &&
                    it.status == RequestStatus.PENDING && it.dayKey == Days.todayKey()
            }
        }
    }

    val state: StateFlow<RestState> = combine(
        AppGraph.store.grants,
        AppGraph.store.emergency,
        AppGraph.sync.profile,
        pending,
        ticker(15_000L),
    ) { _, _, profileState, request, _ ->
        val profile = (profileState as? ProfileState.Ready)?.profile
        RestState(
            appName = AppGraph.engine.limitFor(packageName)?.appName ?: AppGraph.catalog.label(packageName),
            budget = AppGraph.engine.budget(packageName, forceRefresh = true),
            signedIn = profile != null,
            hasBumblebees = profile?.approverIds?.isNotEmpty() == true,
            // Unanswered requests expire; the ticker notices before the backend settles it.
            pendingRequest = request?.takeIf { RequestStatus.effective(it.status, it.createdAtMillis) == RequestStatus.PENDING },
        )
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, RestState())
}

/** A resting app gets a calm dusk sky with the moon out, whatever the hour. */
private val RestingSky = SkyMood(
    top = Color(0xFF3B3F63),
    bottom = Color(0xFFB8A9C9),
    night = true,
    onSky = Color(0xFFF3EEE2),
    greeting = R.string.greeting_night,
)

@Composable
private fun RestScreen(pkg: String, onHome: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel = viewModel(key = "rest-$pkg") { RestViewModel(pkg) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RestContent(pkg, state, onHome, onOpen)
}

/** The resting screen, drawn from plain state (also used for previews and screenshots). */
@Composable
internal fun RestContent(pkg: String, state: RestState, onHome: () -> Unit = {}, onOpen: (String) -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }
    // Keyed by the app: this screen is reused when another app's time runs out (onNewIntent), and
    // an open dialog or what the last app's budget was must not carry over to the next one.
    var asking by rememberSaveable(pkg) { mutableStateOf(false) }
    var emergency by rememberSaveable(pkg) { mutableStateOf(false) }

    // Petals when a Bumblebee's yes arrives while this screen is open.
    var burst by remember(pkg) { mutableIntStateOf(0) }
    var wasUnlocked by remember(pkg) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(pkg, state.unlocked, state.budget != null) {
        val budget = state.budget ?: return@LaunchedEffect
        if (wasUnlocked == false && state.unlocked && !budget.emergency) burst++
        wasUnlocked = state.unlocked
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SkyScene(RestingSky, Modifier.fillMaxSize())
            FallingPetals(Modifier.fillMaxSize(), count = 8)
            // Centred when it fits; scrolls in landscape or with large text, so every button stays reachable.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                        .statusBarsPadding().navigationBarsPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    AppIcon(pkg, size = 56.dp)
                    Box {
                        BloomFlower(
                            if (state.unlocked) 0.9f else 0.05f, Garden.colors.petalPink, Modifier.size(110.dp, 150.dp),
                            asleep = !state.unlocked && state.budget?.sleeping == true,
                        )
                        if (!state.unlocked) SleepyZs(Modifier.align(Alignment.TopEnd))
                    }
                    MeadowCard(Modifier.fillMaxWidth()) {
                        RestCard(state, onAsk = { asking = true }, onEmergency = { emergency = true }, onOpen = { onOpen(pkg) }, onHome = onHome)
                    }
                }
            }
            PetalBurst(burst, Modifier.fillMaxSize())
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        // Inside the provider, so what the dialogs say when they're done shows on this screen.
        if (asking) AskForTimeDialog(packageName = pkg, onDismiss = { asking = false })
        if (emergency) EmergencyDialog(packageName = pkg, appName = state.appName, onDismiss = { emergency = false })
    }
}

@Composable
private fun RestCard(state: RestState, onAsk: () -> Unit, onEmergency: () -> Unit, onOpen: () -> Unit, onHome: () -> Unit) {
    val budget = state.budget
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when {
                state.unlocked && budget?.emergency == true -> stringResource(R.string.rest_title_emergency)
                state.unlocked -> stringResource(R.string.rest_title_yes)
                else -> stringResource(R.string.rest_title, state.appName)
            },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.asHeading(),
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                budget == null -> stringResource(R.string.rest_body_generic)
                state.unlocked && budget.emergency ->
                    stringResource(R.string.rest_body_emergency, state.appName, durationText(budget.emergencyUntilMillis - System.currentTimeMillis()))
                state.unlocked -> stringResource(R.string.rest_body_yes, durationText(budget.remainingMs))
                budget.sleeping -> budget.wakesInMs
                    ?.let { stringResource(R.string.rest_body_hours, state.appName, opensAtText(it)) }
                    ?: stringResource(R.string.rest_body_hours_closed, state.appName)
                budget.weeklyIsTighter -> stringResource(R.string.rest_body_week, minutesText(budget.limit.weeklyMinutes))
                else -> stringResource(R.string.rest_body_today, minutesText(budget.dailyMinutesToday))
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (state.unlocked) {
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rest_open, state.appName)) }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rest_home)) }
        } else {
            // Putting the phone down is the way forward here; asking for more is the other choice.
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rest_home)) }
            when {
                state.pendingRequest != null -> Note(stringResource(R.string.rest_pending, minutesText(state.pendingRequest.minutes)))
                !state.signedIn -> Note(stringResource(R.string.rest_signed_out))
                !state.hasBumblebees -> Note(stringResource(R.string.rest_no_bees))
                else -> OutlinedButton(onClick = onAsk, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rest_ask)) }
            }
            TextButton(onClick = onEmergency) { Text(stringResource(R.string.rest_emergency), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
