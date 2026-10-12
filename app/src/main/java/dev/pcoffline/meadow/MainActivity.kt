package dev.pcoffline.meadow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pcoffline.meadow.data.InviteLinks
import dev.pcoffline.meadow.ui.MeadowRoot
import dev.pcoffline.meadow.ui.theme.MeadowAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A screen to jump to, set by notifications. Consumed once by the navigation host. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            MeadowAppTheme {
                MeadowRoot(pendingRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
        // Invite links: kept in storage so they survive onboarding for people new to Meadow.
        InviteLinks.tokenFrom(intent.data)?.let { AppGraph.store.setPendingInvite(it) }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
