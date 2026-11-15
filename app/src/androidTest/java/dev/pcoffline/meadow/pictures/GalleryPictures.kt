package dev.pcoffline.meadow.pictures

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.showcase.Scenario
import dev.pcoffline.meadow.showcase.Scenarios
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.LocalNow
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.components.LocalSkyWeather
import dev.pcoffline.meadow.ui.components.OfflineBar
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every state of every screen in the debug gallery ([Scenarios]), for the pull request pictures:
 * in the Meadow theme by day, in large text and on a narrow phone. With `looks=all` (main, and a
 * pull request that changes the themes, the sky or the weather), also in each theme by day and by
 * night, and in each weather. The Pictures workflow draws them for the pull request and for its
 * base, and shows the ones that changed. The sky, the weather and the motion are fixed, so two runs
 * of the same code draw the same pixels.
 *
 * Skipped unless run with `-Pandroid.testInstrumentationRunnerArguments.pictures=true`.
 */
@RunWith(AndroidJUnit4::class)
class GalleryPictures {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * One way to look at every screen: its folder [name], what it is in its [group] ([label]), and
     * the sky's [hour], 10:00 by day and 22:00 by night.
     */
    private data class Look(
        val name: String,
        val group: String,
        val label: String,
        val theme: GardenTheme = GardenTheme.MEADOW,
        val mode: DarkMode = DarkMode.LIGHT,
        val hour: Int = 10,
        val fontScale: Float = 1f,
        val narrow: Boolean = false,
    )

    private val everyLook by lazy {
        GardenTheme.entries.flatMap { theme ->
            val group = compose.activity.getString(theme.label)
            listOf(
                Look("${theme.key}-day", group, "Day", theme),
                Look("${theme.key}-night", group, "Night", theme, DarkMode.DARK, hour = 22),
            )
        } + listOf(
            Look("large-text", "Large text", "Day", fontScale = 1.5f),
            Look("narrow", "Narrow phone", "Day", narrow = true),
        )
    }

    /**
     * Whether to draw every look and every weather (`looks=all`): on main, and in a pull request
     * that changes the themes, the sky or the weather. Other pull requests draw [BASIC_LOOKS] and
     * the clear sky only, as their change looks the same in each.
     */
    private val drawsAll get() = InstrumentationRegistry.getArguments().getString("looks") == "all"

    private fun drawn(look: Look, scenario: Scenario) =
        drawsAll || (look.name in BASIC_LOOKS && !scenario.name.startsWith(WEATHER))

    @Before
    fun setUp() {
        Device.requirePictures()
        Device.prepare(animations = false)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @Test
    fun everyScreenInEveryLook() {
        var shown by mutableStateOf(everyLook.first() to 0)
        // Content can only be set once per test, so the look and the screen change through state.
        compose.setContent {
            val (look, index) = shown
            MeadowTheme(look.theme, look.mode) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalSkyHour provides look.hour,
                    LocalSkyWeather provides Weather.CLEAR,
                    LocalDensity provides Density(density.density, density.fontScale * look.fontScale),
                    LocalNow provides { Device.now },
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // A narrow phone's width, in the middle of this one.
                        val frame = if (look.narrow) Modifier.width(NARROW).fillMaxHeight().align(Alignment.Center) else Modifier.fillMaxSize()
                        // As the gallery shows a state: below the status bar, the offline strip on top.
                        Column(frame.statusBarsPadding()) {
                            OfflineBar()
                            Box(Modifier.weight(1f)) {
                                // A fresh start for each state, so nothing carries over from the last one.
                                key(look, index) { SnackbarScope { Scenarios.all[index].content() } }
                            }
                        }
                    }
                }
            }
        }
        // The window fades in as the test starts: the first picture waits until it has.
        compose.waitForIdle()
        Thread.sleep(WINDOW_MS)
        Device.writeSystemBars("gallery", compose.activity)
        // Each screen in each look in turn, and an index with their names, in this order. What
        // isn't drawn is listed too, so the workflow doesn't count it as gone.
        val index = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        Scenarios.all.forEachIndexed { i, scenario ->
            everyLook.forEach { look ->
                val path = "${look.name}/${slug(scenario.screen)}--${slug(scenario.name)}.png"
                if (!drawn(look, scenario)) {
                    skipped += path
                    return@forEach
                }
                shown = look to i
                compose.waitForIdle()
                Thread.sleep(SETTLE_MS)
                // A picture that loaded late changes the screen once more.
                compose.waitForIdle()
                Device.screenshot("gallery/$path")
                index += listOf(path, scenario.screen, scenario.name, look.label, look.group).joinToString("\t")
            }
        }
        Device.write("gallery/index.tsv", index.joinToString("\n"))
        Device.write("gallery/skipped.tsv", skipped.joinToString("\n"))
    }

    private companion object {
        val NARROW = 320.dp

        /** The looks that every pull request draws: one theme by day, large text and a narrow phone. */
        val BASIC_LOOKS = setOf("meadow-day", "large-text", "narrow")

        /** The start of each weather's state, over the lived-in garden. */
        const val WEATHER = "Weather:"

        /** Long enough for pictures to load; the motion is off, so nothing else moves. */
        const val SETTLE_MS = 300L

        /** Longer than a window takes to fade in. */
        const val WINDOW_MS = 2_000L

        fun slug(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
