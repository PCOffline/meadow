package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.a11y.contrastProblems
import dev.pcoffline.meadow.data.BeeStats
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.GardenUnavailable
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.CircleContent
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.requests.BeeStatsContent
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.setup.SetupScreen
import dev.pcoffline.meadow.ui.setup.StrictGateContent
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Draws every screen in every theme, by day and by night, and measures the contrast of each piece
 * of text in the pixels (see TextContrast). A tall window keeps most of each screen in view.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class RenderedContrastTest : ScreenTest() {
    private val looks = GardenTheme.entries.flatMap { theme -> listOf(DarkMode.LIGHT, DarkMode.DARK).map { theme to it } }

    /**
     * Shows [content] for each of [variants] in each look in turn, and fails with every unreadable
     * text in all of them.
     */
    private fun <T> sweep(variants: List<T>, content: @Composable (T) -> Unit) {
        var look by mutableStateOf(looks.first())
        var variant by mutableStateOf(variants.first())
        compose.setContent { MeadowTheme(look.first, look.second) { AppBackground { content(variant) } } }
        val problems = variants.flatMap { v ->
            looks.flatMap { next ->
                variant = v
                look = next
                compose.waitForIdle()
                val where = listOfNotNull(v.takeIf { variants.size > 1 }, next.first.name, if (next.second == DarkMode.DARK) "night" else "day")
                compose.contrastProblems().map { "${where.joinToString("/")}: $it" }
            }
        }
        assertTrue("Unreadable text:\n" + problems.joinToString("\n") { "- $it" }, problems.isEmpty())
    }

    private fun sweep(content: @Composable () -> Unit) = sweep(listOf(Unit)) { content() }

    @Test
    fun `the garden, under every sky`() =
        // Morning, afternoon, evening and night skies each put their own colours behind the header.
        sweep(listOf(8, 13, 19, 23)) { hour ->
            CompositionLocalProvider(LocalSkyHour provides hour) { GardenContent(DemoGarden.robin, DemoGarden.garden) }
        }

    @Test
    fun `an empty garden and its notices`() = sweep {
        GardenContent(DemoGarden.robin.copy(approverIds = emptyList()), DemoGarden.garden.copy(essentialsReady = false, budgets = emptyList()))
    }

    @Test
    fun `weekly stats`() = sweep { StatsContent(DemoGarden.stats) }

    @Test
    fun `monthly stats, with the calendar`() = sweep { StatsContent(DemoGarden.monthStats) }

    @Test
    fun `stats without usage access`() = sweep { StatsContent(DemoGarden.stats.copy(hasPermission = false)) }

    @Test
    fun `the circle`() = sweep {
        CircleContent(DemoGarden.robin, listOf(DemoGarden.maya, DemoGarden.theo), listOf(DemoGarden.ana), emptyList(), emptyList(), anonymous = false)
    }

    @Test
    fun `a chat with a request in it`() = sweep {
        ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, requestCard = { _, _ -> RequestCard(DemoGarden.request, DemoGarden.robin.uid) })
    }

    @Test
    fun `requests in every state`() = sweep {
        androidx.compose.foundation.layout.Column {
            dev.pcoffline.meadow.data.RequestStatus.entries.forEach { status ->
                RequestCard(DemoGarden.request.copy(id = status.name, status = status), DemoGarden.maya.uid, compact = true)
            }
        }
    }

    @Test
    fun `a resting app`() = sweep { RestContent("demo.tiktok", DemoGarden.rest) }

    @Test
    fun `a resting app with a request waiting`() = sweep { RestContent("demo.tiktok", DemoGarden.rest.copy(pendingRequest = DemoGarden.request.copy(status = dev.pcoffline.meadow.data.RequestStatus.PENDING))) }

    @Test
    fun `onboarding`() = sweep { AuthScreen() }

    @Test
    fun `settings`() = sweep { SettingsScreen(DemoGarden.robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }

    @Test
    fun `profile`() = sweep { ProfileScreen(DemoGarden.robin, onBack = {}) }

    @Test
    fun `setup`() = sweep { SetupScreen(onDone = {}) }

    @Test
    fun `strict mode needs more permissions`() = sweep {
        StrictGateContent(ProtectionState(true, true, true, true, false, batteryUnrestricted = false, notificationListener = false), error = text(R.string.error_unavailable))
    }

    @Test
    fun `a Bumblebee's own stats`() = sweep {
        BeeStatsContent(
            BeeStats(
                asked = 14, yes = 9, no = 3, missed = 1, waiting = 1, minutesGranted = 135, leniency = 68,
                people = listOf(BeeStats.Person("robin", "Robin", "🦔", 10, 7, 2), BeeStats.Person("ana", "Ana", "🦊", 4, 2, 1)),
            ),
        )
    }

    @Test
    fun `the tab bar, with counts`() = sweep {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.BottomCenter)) { MeadowTabBar(Tab.GARDEN, Badges(requests = 2, invites = 1, chats = 3), onSelect = {}) }
        }
    }

    @Test
    fun `a snackbar`() = sweep {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(host) { host.showSnackbar("Sent to your Bumblebees", actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
        Box(Modifier.fillMaxSize()) { SnackbarHost(host, Modifier.align(Alignment.BottomCenter)) }
    }

    @Test
    fun `the garden couldn't load`() = sweep { GardenUnavailable(IllegalStateException("test")) }
}
