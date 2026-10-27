package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.colorSchemeFor
import dev.pcoffline.meadow.ui.theme.gardenColorsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Growth's numbers: honest about days it has no record of, and coloured by whether they're good news. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StatsTest : ScreenTest() {
    private val week = DemoGarden.stats
    private val min = 60_000L

    /** The colour just inside the pill holding [text], beside the words. */
    private fun pillColour(text: String): Color {
        val bounds = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        return shot[(bounds.left - 4).toInt(), bounds.center.y.toInt()]
    }

    private fun assertClose(expected: Color, actual: Color) = assertTrue(
        "expected $expected, was $actual",
        abs(expected.red - actual.red) < 0.02f && abs(expected.green - actual.green) < 0.02f && abs(expected.blue - actual.blue) < 0.02f,
    )

    @Test
    fun `less screen time than before is green`() {
        show { StatsContent(week) }
        assertClose(gardenColorsFor(GardenTheme.MEADOW, dark = false).better, pillColour("less than"))
    }

    @Test
    fun `more screen time than before is red`() {
        show { StatsContent(week.copy(stats = week.stats!!.copy(previousTotalMs = 600 * min))) }
        assertClose(colorSchemeFor(GardenTheme.MEADOW, dark = false).errorContainer, pillColour("more than"))
    }

    @Test
    fun `a day with no record is said so, left out of the average, and nothing is compared`() {
        val s = week.stats!!
        // Monday has no record: the rest add up to 6 days of 60 minutes.
        val days = s.days.mapIndexed { i, (day, _) -> day to if (i == 0) null else 60 * min }
        show { StatsContent(week.copy(stats = s.copy(days = days, totalMs = 360 * min, previousTotalMs = null))) }
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.stats_missing_days, 1, 1)).assertExists()
        compose.onNodeWithText("1h").assertExists()
        assertTrue(compose.onAllNodes(hasText("than", substring = true)).fetchSemanticsNodes().isEmpty())
        assertEquals(60 * min, s.copy(days = days).dailyAverageMs(s.to))
    }
}
