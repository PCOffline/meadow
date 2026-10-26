package dev.pcoffline.meadow.ui.theme

import android.app.Application
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Text with no colour of its own, on screens without a Surface, must still be readable. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ThemeContentColorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `plain text uses the theme's text colour, by day and by night`() {
        val cases = GardenTheme.entries.flatMap { theme -> listOf(DarkMode.LIGHT, DarkMode.DARK).map { theme to it } }
        val seen = mutableMapOf<Pair<GardenTheme, DarkMode>, Pair<Color, Color>>()
        compose.setContent {
            cases.forEach { case ->
                MeadowTheme(case.first, case.second) {
                    seen[case] = LocalContentColor.current to MaterialTheme.colorScheme.background
                }
            }
        }
        compose.waitForIdle()
        cases.forEach { case ->
            val (text, background) = seen.getValue(case)
            assertEquals("$case text colour", colorSchemeFor(case.first, case.second == DarkMode.DARK).onBackground, text)
            assertTrue("$case text is only %.2f:1".format(contrast(text, background)), contrast(text, background) >= 4.5f)
        }
    }
}
