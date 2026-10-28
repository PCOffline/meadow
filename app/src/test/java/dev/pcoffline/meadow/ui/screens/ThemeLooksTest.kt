package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.CompositionLocalProvider
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.gardenColorsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** What each theme draws: mushrooms in the mushroom forest, and theme previews in their own colours. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeLooksTest : ScreenTest() {
    private fun PixelMap.count(colour: Color, left: Int = 0, top: Int = 0, right: Int = width, bottom: Int = height): Int {
        var n = 0
        for (x in left until right) for (y in top until bottom) {
            val c = this[x, y]
            if (abs(c.red - colour.red) < 0.012f && abs(c.green - colour.green) < 0.012f && abs(c.blue - colour.blue) < 0.012f) n++
        }
        return n
    }

    private fun flower(theme: GardenTheme): PixelMap {
        show(theme) { BloomFlower(1f, Color.Red, Modifier.size(90.dp, 120.dp), sway = false, growIn = false) }
        return compose.onNode(isRoot()).captureToImage().toPixelMap()
    }

    @Test
    fun `the mushroom forest grows mushrooms on pale stalks, and other gardens flowers`() {
        val stalk = Color(0xFFF1E6D2)
        val mushroom = flower(GardenTheme.MUSHROOM)
        assertTrue(mushroom.count(stalk) > 100)
        // A red cap with pale spots on it.
        assertTrue(mushroom.count(Color.Red) > 300)
        assertEquals(0, flower(GardenTheme.MEADOW).count(stalk))
    }

    @Test
    fun `each theme's preview shows that theme's own sun`() {
        AppGraph.store.setAppearance(GardenTheme.MEADOW.key, DarkMode.LIGHT.key)
        show { AppearanceCard() }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        GardenTheme.entries.forEach { theme ->
            val card = compose.onNode(hasText(text(theme.label), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val sun = gardenColorsFor(theme, dark = false).sun
            val inCard = shot.count(sun, card.left.toInt(), card.top.toInt(), card.right.toInt(), card.bottom.toInt())
            assertTrue("$theme: $inCard", inCard > 30)
        }
    }

    @Test
    fun `the sun sits clear below your picture and Settings`() {
        show { CompositionLocalProvider(LocalSkyHour provides 14) { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        val sun = gardenColorsFor(GardenTheme.MEADOW, dark = false).sun
        val row = listOf(R.string.action_settings, R.string.profile_open).map {
            compose.onNode(hasContentDescription(text(it))).fetchSemanticsNode().boundsInRoot
        }
        val rowBottom = row.maxOf { it.bottom }
        val sunTop = (0 until shot.height).first { y -> (0 until shot.width).any { x -> shot.count(sun, x, y, x + 1, y + 1) == 1 } }
        // The sun's rays reach 15dp past its disc; they end above the row's bottom edge, not on it.
        val clearance = (sunTop - rowBottom) / compose.density.density
        assertTrue("the sun's disc starts ${clearance}dp below the row", clearance >= 20f)
    }
}
