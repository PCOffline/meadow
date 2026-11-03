package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Clock in the app's own theme (#83), and the four mode choices on a narrow phone. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w360dp-h1600dp-xxhdpi")
class DarkModeChoiceTest : ScreenTest() {
    private fun darkAt(hour: Int): Boolean {
        var dark: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalSkyHour provides hour) {
                MeadowTheme(darkMode = DarkMode.CLOCK) {
                    dark = LocalDarkTheme.current
                    Text("x")
                }
            }
        }
        compose.waitForIdle()
        return dark!!
    }

    @Test
    fun `Clock makes the app light at noon`() = assertEquals(false, darkAt(12))

    @Test
    fun `Clock makes the app dark at night`() = assertEquals(true, darkAt(22))

    @Test
    fun `every mode's name fits its button on a narrow phone`() {
        show { SettingsScreen(DemoGarden.robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }
        DarkMode.entries.forEach { mode ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text(mode.label)).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val l = layouts.single()
            val name = text(mode.label)
            // Not shortened with "…", and the whole line fits its box (a pixel of rounding allowed).
            assertFalse("$name ends in …", (0 until l.lineCount).any { l.isLineEllipsized(it) })
            assertTrue("$name is cut off: ${l.getLineRight(0)} of ${l.size.width}", l.lineCount == 1 && l.getLineRight(0) <= l.size.width + 1f)
        }
    }
}
