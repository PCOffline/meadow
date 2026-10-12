package dev.pcoffline.meadow.showcase

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.CircleContent
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.components.PetalBurst
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.stats.StatsContent
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
        Thread.sleep(800) // Let images and entrance animations settle.
        shell("screencap -p $DIR/$name.png")
    }

    /** Records the screen while [drive] plays animations frame by frame on the test clock. */
    private fun record(name: String, seconds: Int, drive: () -> Unit) {
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
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
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
        val looks = listOf(
            Triple(GardenTheme.BLOSSOM, DarkMode.LIGHT, 8),
            Triple(GardenTheme.LAVENDER, DarkMode.DARK, 22),
            Triple(GardenTheme.SUNFLOWER, DarkMode.LIGHT, 14),
            Triple(GardenTheme.POND, DarkMode.DARK, 23),
        )
        var look by mutableStateOf(looks.first())
        // Content can only be set once per test, so the look changes through state.
        compose.setContent {
            val (theme, mode, hour) = look
            MeadowTheme(theme, mode) {
                SystemBars()
                CompositionLocalProvider(LocalSkyHour provides hour) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        WithTabs(Tab.GARDEN) { GardenContent(DemoGarden.robin, DemoGarden.garden) }
                    }
                }
            }
        }
        looks.forEach {
            look = it
            capture("theme-${it.first.key}")
        }
    }

    @Test
    fun stats() {
        show { WithTabs(Tab.STATS) { StatsContent(DemoGarden.stats) } }
        capture("stats")
    }

    @Test
    fun circle() {
        show {
            WithTabs(Tab.CIRCLE) {
                CircleContent(DemoGarden.robin, listOf(DemoGarden.maya, DemoGarden.theo), listOf(DemoGarden.ana), emptyList(), emptyList(), anonymous = false)
            }
        }
        capture("circle")
    }

    @Test
    fun chat() {
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, requestCard = {
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
    fun profile() {
        show { ProfileScreen(DemoGarden.robin, onBack = {}) }
        compose.onNodeWithText(compose.activity.getString(R.string.profile_theme)).performScrollTo()
        capture("profile")
    }

    @Test
    fun gardenMovie() {
        var celebration by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        show {
            WithTabs(Tab.GARDEN) { GardenContent(DemoGarden.robin, DemoGarden.garden) }
            PetalBurst(celebration, Modifier.fillMaxSize())
        }
        record("garden", seconds = 7) {
            play(2_500)
            celebration++
            play(4_500)
        }
    }

    @Test
    fun welcomeMovie() {
        compose.mainClock.autoAdvance = false
        show { AuthScreen() }
        val next = compose.activity.getString(R.string.intro_next)
        record("welcome", seconds = 9) {
            play(2_600)
            compose.onNodeWithText(next).performClick()
            play(2_800)
            compose.onNodeWithText(next).performClick()
            play(3_000)
        }
    }

    private companion object {
        const val DIR = "/sdcard/Pictures/meadow"
    }
}
