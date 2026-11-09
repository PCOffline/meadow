package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.home.GardenContent
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** In landscape, a cutout or the navigation bar takes a side: the sky reaches under it, the words don't. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w800dp-h400dp-land")
class GardenSidesTest : ScreenTest() {
    private val side = 48.dp
    private val width = 720.dp
    private var page = Color.Unspecified

    private fun showWithSides() = show {
        page = MaterialTheme.colorScheme.background
        Box(Modifier.size(width, 380.dp)) {
            GardenContent(DemoGarden.robin, DemoGarden.garden, sides = PaddingValues(horizontal = side))
        }
    }

    @Test
    fun `the sky reaches the edges of the screen`() {
        showWithSides()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val px = compose.density.density
        val y = (60 * px).toInt()
        // Under the left side and under the right side, the sky shows, not the page.
        assertNotEquals(page.toArgb(), bitmap.getPixel((4 * px).toInt(), y))
        assertNotEquals(page.toArgb(), bitmap.getPixel(((width.value - 4) * px).toInt(), y))
    }

    @Test
    fun `the words, cards and buttons keep clear of the sides`() {
        showWithSides()
        val greeting = compose.onNodeWithText(DemoGarden.robin.displayName, substring = true).getBoundsInRoot()
        assertTrue("greeting starts at ${greeting.left}", greeting.left >= side + 20.dp)
        val settings = compose.onNodeWithContentDescription(text(R.string.action_settings)).getBoundsInRoot()
        assertTrue("Settings ends at ${settings.right}", settings.right <= width - side)
        val plant = compose.onNodeWithContentDescription(text(R.string.garden_plant)).getBoundsInRoot()
        assertTrue("Plant ends at ${plant.right}", plant.right <= width - side)
        val title = node(R.string.garden_title).getBoundsInRoot()
        assertTrue("title starts at ${title.left}", title.left >= side)
    }
}
