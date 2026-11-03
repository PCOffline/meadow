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
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.showcase.Scenarios
import dev.pcoffline.meadow.ui.SnackbarScope
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
 * Every state of every screen in the debug gallery ([Scenarios]), in each [Look], for the pull
 * request pictures. The Pictures workflow draws them for the pull request and for its base, and
 * shows the ones that changed. The sky, the weather and the motion are fixed, so two runs of the
 * same code draw the same pixels.
 *
 * Skipped unless run with `-Pandroid.testInstrumentationRunnerArguments.pictures=true`.
 */
@RunWith(AndroidJUnit4::class)
class GalleryPictures {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** One way to look at every screen. */
    private data class Look(
        val name: String,
        val theme: GardenTheme = GardenTheme.MEADOW,
        val mode: DarkMode = DarkMode.LIGHT,
        val fontScale: Float = 1f,
        val narrow: Boolean = false,
    )

    private val looks = listOf(
        Look("light"),
        Look("dark", mode = DarkMode.DARK),
        Look("large-text", fontScale = 1.5f),
        Look("narrow", narrow = true),
    ) + GardenTheme.entries.filter { it != GardenTheme.MEADOW }.map { Look("theme-${it.key}", theme = it) }

    @Before
    fun setUp() {
        Device.requirePictures()
        Device.prepare(animations = false)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @Test
    fun everyScreenInEveryLook() {
        var shown by mutableStateOf(looks.first() to 0)
        // Content can only be set once per test, so the look and the screen change through state.
        compose.setContent {
            val (look, index) = shown
            MeadowTheme(look.theme, look.mode) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalSkyHour provides 10,
                    LocalSkyWeather provides Weather.CLEAR,
                    LocalDensity provides Density(density.density, density.fontScale * look.fontScale),
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
        val names = mutableListOf<String>()
        looks.forEach { look ->
            Scenarios.all.forEachIndexed { i, scenario ->
                shown = look to i
                compose.waitForIdle()
                Thread.sleep(SETTLE_MS)
                val name = "gallery/${look.name}/${slug(scenario.screen)}--${slug(scenario.name)}.png"
                Device.screenshot(name)
                names += name
            }
        }
        Device.write("gallery/index.txt", names.joinToString("\n"))
    }

    private companion object {
        val NARROW = 320.dp

        /** Long enough for pictures to load; the motion is off, so nothing else moves. */
        const val SETTLE_MS = 300L

        fun slug(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
