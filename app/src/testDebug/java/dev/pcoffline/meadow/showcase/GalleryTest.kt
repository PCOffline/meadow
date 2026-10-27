package dev.pcoffline.meadow.showcase

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.ui.screens.ScreenTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The debug builds' screen gallery: every state draws, and the list leads to each one. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class GalleryTest : ScreenTest() {
    @After
    fun backOnline() = Connectivity.set(true)

    @Test
    fun `every screen in the gallery draws in each of its states`() {
        var shown by mutableIntStateOf(0)
        show { key(shown) { Scenarios.all[shown].content() } }
        Scenarios.all.indices.forEach { i ->
            shown = i
            val scenario = Scenarios.all[i]
            runCatching { compose.waitForIdle() }.onFailure { throw AssertionError("${scenario.screen}: ${scenario.name}", it) }
        }
    }

    @Test
    fun `every state has its own name`() {
        val names = Scenarios.all.map { it.screen to it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `the list opens a state, and pretending to be offline shows the strip`() {
        show { Gallery() }
        compose.onNode(hasText("Offline") and hasClickAction()).performClick()
        assertFalse(Connectivity.online.value)
        compose.onNode(hasText("Nothing planted yet")).performScrollTo().performClick()
        node(R.string.garden_empty_title).assertExists()
        node(R.string.offline_bar).assertExists()
    }
}
