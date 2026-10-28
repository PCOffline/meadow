package dev.pcoffline.meadow.showcase

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.settings.PrivacyPreviewContent
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots and screen recordings for the README, drawn from [DemoGarden].
 *
 * Skipped unless run with `-Pandroid.testInstrumentationRunnerArguments.showcase=true`.
 * Files land in /sdcard/Pictures/meadow; the Screenshots workflow pulls and publishes them.
 */
@RunWith(AndroidJUnit4::class)
class ShowcaseTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Before
    fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("showcase") == "true")
        shell("mkdir -p $DIR")
        // A freshly booted emulator's launcher often shows "isn't responding"; keep it off screen.
        shell("settings put global hide_error_dialogs 1")
        shell("am force-stop com.google.android.apps.nexuslauncher")
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        // A tidy status bar: fixed time, full signal and battery, no notification icons.
        shell("settings put global sysui_demo_allowed 1")
        listOf(
            "enter",
            "clock -e hhmm 0941",
            "battery -e level 100 -e plugged false",
            "network -e wifi show -e level 4",
            "network -e mobile show -e datatype none -e level 4",
            "notifications -e visible false",
        ).forEach { shell("am broadcast -a com.android.systemui.demo -e command $it") }
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    private fun shell(command: String) {
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // A screen can be captured before its window is in front, or before it's drawn: then it's
        // one flat colour. Wait for it, and never save a blank page.
        val until = System.currentTimeMillis() + 15_000
        while ((!focused() || blank()) && System.currentTimeMillis() < until) Thread.sleep(250)
        check(!blank()) { "$name stayed blank" }
        Thread.sleep(800) // Let images and entrance animations settle.
        shell("screencap -p $DIR/$name.png")
    }

    /** Whether the screen below the status bar is all one colour. */
    private fun blank(): Boolean {
        val shot = automation.takeScreenshot() ?: return true
        try {
            val first = shot.getPixel(shot.width / 2, shot.height / 3)
            return (1 until 20).all { i ->
                shot.getPixel(shot.width * (i % 5 + 1) / 6, shot.height * (i / 5 + 2) / 7) == first
            }
        } finally {
            shot.recycle()
        }
    }

    private fun focused(): Boolean {
        var focused = false
        compose.runOnUiThread { focused = compose.activity.hasWindowFocus() }
        return focused
    }

    /** Records the screen while [drive] plays animations frame by frame on the test clock. */
    private fun record(name: String, seconds: Int, drive: () -> Unit) {
        play(600) // Draw the first frames before recording starts.
        val recording = automation.executeShellCommand("screenrecord --time-limit $seconds --bit-rate 8000000 $DIR/$name.mp4")
        Thread.sleep(400)
        drive()
        ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
    }

    /** Advances the (paused) test clock in real time, so ambient animations play on screen. */
    private fun play(millis: Long) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            compose.mainClock.advanceTimeBy(16)
            Thread.sleep(12)
        }
    }

    private fun show(theme: GardenTheme = GardenTheme.MEADOW, mode: DarkMode = DarkMode.LIGHT, hour: Int = 10, content: @Composable () -> Unit) {
        compose.setContent {
            MeadowTheme(theme, mode) {
                SystemBars()
                CompositionLocalProvider(LocalSkyHour provides hour) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SnackbarScope(content) }
                }
            }
        }
    }

    @Composable
    private fun SystemBars() {
        val dark = LocalDarkTheme.current
        val view = LocalView.current
        SideEffect {
            val window = compose.activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    @Composable
    private fun WithTabs(tab: Tab, content: @Composable () -> Unit) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { MeadowTabBar(tab, Badges(requests = 1, invites = 0, chats = 1)) {} },
        ) { padding -> Box(Modifier.padding(padding)) { content() } }
    }

    @Test
    fun garden() {
        show { WithTabs(Tab.GARDEN) { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
        capture("garden")
    }

    @Test
    fun themes() {
        // Every theme, through a day: the sun climbs and sets across them, then night falls.
        val looks = listOf(
            Triple(GardenTheme.BLOSSOM, DarkMode.LIGHT, 7),
            Triple(GardenTheme.MEADOW, DarkMode.LIGHT, 10),
            Triple(GardenTheme.POND, DarkMode.LIGHT, 13),
            Triple(GardenTheme.SUNFLOWER, DarkMode.LIGHT, 16),
            Triple(GardenTheme.MUSHROOM, DarkMode.LIGHT, 19),
            Triple(GardenTheme.LAVENDER, DarkMode.DARK, 22),
        )
        var look by mutableStateOf(looks.first())
        // Content can only be set once per test, so the look changes through state.
        compose.setContent {
            val (theme, mode, hour) = look
            MeadowTheme(theme, mode) {
                SystemBars()
                CompositionLocalProvider(LocalSkyHour provides hour) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SnackbarScope { WithTabs(Tab.GARDEN) { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
                    }
                }
            }
        }
        // Numbered, so the strip keeps the day's order.
        looks.forEachIndexed { i, it ->
            look = it
            capture("theme-${i + 1}-${it.first.key}")
        }
    }

    @Test
    fun appearance() {
        show { Box(Modifier.statusBarsPadding().padding(16.dp)) { AppearanceCard() } }
        capture("appearance")
    }

    @Test
    fun stats() {
        show { WithTabs(Tab.STATS) { StatsContent(DemoGarden.stats) } }
        capture("stats")
    }

    @Test
    fun people() {
        show { WithTabs(Tab.PEOPLE) { PeopleContent(DemoGarden.robin, DemoGarden.people.copy(incomingRequests = emptyList(), outgoingRequests = emptyList())) } }
        capture("people")
    }

    @Test
    fun chat() {
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, requestCard = { _, _ ->
                RequestCard(DemoGarden.request, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp), compact = true)
            })
        }
        capture("chat")
    }

    @Test
    fun resting() {
        show(hour = 21) { RestContent("demo.tiktok", DemoGarden.rest) }
        capture("resting")
    }

    @Test
    fun limit() {
        val youtube = AppLimit("demo.youtube", "YouTube", 60, 0)
        val state = LimitEditorState(
            existing = youtube, appName = "YouTube", budget = AppBudget(youtube, 52 * MIN, 240 * MIN, 0, 0), bumblebeeCount = 2, loaded = true,
        )
        show {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                BackTopBar(state.appName, onBack = {})
                LimitEditor(youtube.packageName, state, onDone = {})
            }
        }
        capture("limit")
    }

    @Test
    fun friend() {
        show {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                BackTopBar(DemoGarden.anaPage.profile.displayName, onBack = {})
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) { FriendPage(DemoGarden.anaPage, false) }
            }
        }
        capture("friend")
    }

    @Test
    fun privacy() {
        show {
            Box(Modifier.navigationBarsPadding()) {
                PrivacyPreviewContent(DemoGarden.robin, DemoGarden.today, DemoGarden.progress, DemoGarden.myLimits, save = {})
            }
        }
        capture("privacy")
    }

    @Test
    fun emergency() {
        val rest = DemoGarden.rest
        show(hour = 21) { RestContent("demo.tiktok", rest.copy(budget = DemoGarden.tiktok.copy(emergencyUntilMillis = System.currentTimeMillis() + 30 * MIN))) }
        capture("emergency")
    }

    @Test
    fun profile() {
        show { ProfileScreen(DemoGarden.robin, onBack = {}) }
        capture("profile")
    }

    @Test
    fun welcomeMovie() {
        compose.mainClock.autoAdvance = false
        show { AuthScreen() }
        val next = compose.activity.getString(R.string.intro_next)
        record("welcome", seconds = 10) {
            play(2_600)
            compose.onNodeWithText(next).performClick()
            play(3_000)
            compose.onNodeWithText(next).performClick()
            play(3_600)
        }
    }

    private companion object {
        const val DIR = "/sdcard/Pictures/meadow"
        const val MIN = 60_000L
    }
}
