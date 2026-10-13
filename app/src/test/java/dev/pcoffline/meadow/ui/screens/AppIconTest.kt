package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.ui.components.AppIcon
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The block screen is reused for the next app, so an icon must follow the app it's drawn for. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppIconTest : ScreenTest() {
    private fun placeholderShown() = compose.onAllNodesWithText("🌱").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the icon changes with the app`() {
        val own = app.packageName
        assumeTrue("needs an icon for this app", AppGraph.catalog.icon(own) != null)
        var pkg by mutableStateOf(own)
        show { AppIcon(pkg) }
        compose.waitForIdle()
        assertTrue("this app's icon should show", !placeholderShown())

        // An app with no icon to load: the last app's icon must not stay in its place.
        pkg = "dev.pcoffline.meadow.not.installed"
        compose.waitUntil(5_000) { placeholderShown() }
    }
}
