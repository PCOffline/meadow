package dev.pcoffline.meadow.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.Gallery
import dev.pcoffline.meadow.showcase.Scenarios
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every state of every screen in the debug gallery draws, says something, and stays accessible,
 * by day and by night: the same states the Pictures workflow draws for pull requests.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class GalleryTest : ScreenTest() {
    @Test
    fun `every state of every screen draws by day and by night`() {
        var index by mutableStateOf(0)
        var mode by mutableStateOf(DarkMode.LIGHT)
        compose.setContent {
            MeadowTheme(GardenTheme.MEADOW, mode) {
                AppBackground { Column(Modifier.fillMaxSize()) { SnackbarScope { Scenarios.all[index].content() } } }
            }
        }
        val failed = ArrayList<String>()
        Scenarios.all.forEachIndexed { i, scenario ->
            for (look in listOf(DarkMode.LIGHT, DarkMode.DARK)) {
                index = i
                mode = look
                val drawn = runCatching {
                    compose.waitForIdle()
                    val said = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                    check(said.isNotEmpty()) { "says nothing" }
                    assertAccessible(requireHeading = false)
                }
                drawn.exceptionOrNull()?.let { failed += "${scenario.screen} / ${scenario.name} ($look): ${it.message?.lineSequence()?.firstOrNull()}" }
            }
        }
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `the gallery lists the states, and opens one`() {
        compose.setContent { Gallery() }
        node(R.string.gallery_label).assertExists()
        compose.onNodeWithText(text(DarkMode.DARK.label)).performClick()
        compose.onNodeWithText("Offline").performClick()
        val first = Scenarios.all.first()
        compose.onNodeWithText(first.name).performScrollTo().performClick()
        node(R.string.gallery_label).assertDoesNotExist()
    }
}
