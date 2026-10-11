package dev.pcoffline.meadow.enforcement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowHills
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/** The calm screen shown in place of an app whose time is up. */
class BlockActivity : ComponentActivity() {
    private val packageState = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        packageState.value = intent.getStringExtra(EXTRA_PACKAGE)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            MeadowTheme {
                val pkg by packageState.collectAsStateWithLifecycle()
                pkg?.let { RestScreen(it, onHome = ::goHome, onOpen = ::openApp) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        packageState.value = intent.getStringExtra(EXTRA_PACKAGE)
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"

        fun launch(context: Context, packageName: String) {
            val intent = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
private fun RestScreen(pkg: String, onHome: () -> Unit, onOpen: (String) -> Unit) {
    val garden = Garden.colors
    val snackbar = remember { SnackbarHostState() }
    var asking by remember { mutableStateOf(false) }
    var emergency by remember { mutableStateOf(false) }
    val grants by AppGraph.store.grants.collectAsStateWithLifecycle()
    val activeEmergency by AppGraph.store.emergency.collectAsStateWithLifecycle()
    val budget by produceState<AppBudget?>(null, pkg, grants, activeEmergency) {
        while (true) {
            value = withContext(Dispatchers.IO) { AppGraph.engine.budget(pkg, forceRefresh = true) }
            delay(15_000L)
        }
    }
    val profileState by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val profile = (profileState as? ProfileState.Ready)?.profile
    val outgoing by remember(profile?.uid) {
        profile?.uid?.let { AppGraph.requests.outgoing(it) } ?: emptyFlow<List<LimitRequest>>()
    }.collectAsStateWithLifecycle(emptyList())
    val pending = outgoing.firstOrNull {
        it.type == RequestType.EXTRA_TIME && it.packageName == pkg &&
            it.status == RequestStatus.PENDING && it.dayKey == Days.todayKey()
    }
    val appName = remember(pkg) { AppGraph.engine.limitFor(pkg)?.appName ?: AppGraph.catalog.label(pkg) }
    val b = budget
    val unlocked = b != null && !b.exhausted

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize().background(garden.sky)) {
            MeadowHills(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter))
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                AppIcon(pkg, size = 56.dp)
                BloomFlower(if (unlocked) 0.9f else 0.05f, garden.petalPink, Modifier.size(110.dp, 150.dp))
                Text(
                    when {
                        unlocked && b?.emergency == true -> "Emergency unlock active 🚨"
                        unlocked -> "Your friend said yes 🌸"
                        else -> "$appName is resting 🌙"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    when {
                        unlocked && b!!.emergency ->
                            "$appName is open for ${formatDuration(b.emergencyUntilMillis - System.currentTimeMillis())}. Take care of what you need to."
                        unlocked -> "You have ${formatDuration(b!!.remainingMs)} more. Enjoy it mindfully."
                        b != null && b.weeklyIsTighter ->
                            "You've used the ${formatMinutes(b.limit.weeklyMinutes)} you set aside for this week."
                        b != null ->
                            "You've used the ${formatMinutes(b.limit.dailyMinutes)} you set aside today. It blooms again tomorrow."
                        else -> "You've used the time you set aside."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    unlocked -> Button(onClick = { onOpen(pkg) }, modifier = Modifier.fillMaxWidth()) { Text("Open $appName") }
                    pending != null -> Text(
                        "💌 You asked for ${pending.minutes} more minutes. We'll let you know when someone answers.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    profile == null -> Text(
                        "Sign in to Meadow to ask a friend for more time.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    profile.approverIds.isEmpty() -> Text(
                        "Invite an approver in Meadow so you can ask a friend for more time.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    else -> Button(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) { Text("Ask a friend for more time") }
                }
                OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go do something lovely") }
                if (!unlocked) {
                    TextButton(onClick = { emergency = true }) {
                        Text("Emergency? Unlock now", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
    if (asking) AskForTimeDialog(packageName = pkg, onDismiss = { asking = false })
    if (emergency) EmergencyDialog(packageName = pkg, appName = appName, onDismiss = { emergency = false })
}
