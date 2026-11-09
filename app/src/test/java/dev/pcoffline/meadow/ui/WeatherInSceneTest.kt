package dev.pcoffline.meadow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.ui.components.SkyMood
import dev.pcoffline.meadow.ui.components.SkyScene
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The weather stays inside the scene: no rain, snow or clouds on the page around it. */
@RunWith(AndroidJUnit4::class)
class WeatherInSceneTest {
    @get:Rule val compose = createComposeRule()

    private val page = Color.White
    private val margin = 40

    /** The scene in [weather], with [margin] dp of plain page above and below it and on each side. */
    private fun show(weather: Weather, night: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MeadowTheme {
                Box(Modifier.size(240.dp, 240.dp).background(page).testTag("page")) {
                    SkyScene(
                        SkyMood(Color(0xFF9FB4C8), Color(0xFFDCE3EA), night, Color.Black, R.string.greeting_morning, weather),
                        Modifier.padding(margin.dp).fillMaxSize(),
                    )
                }
            }
        }
    }

    /** How many pixels of the page outside the scene aren't the page's colour. */
    private fun paintedOutside(): Int {
        val bitmap = compose.onNodeWithTag("page").captureToImage().asAndroidBitmap()
        val edge = (margin * compose.density.density).toInt() - 1
        var painted = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val outside = x < edge || y < edge || x >= bitmap.width - edge || y >= bitmap.height - edge
            if (outside && bitmap.getPixel(x, y) != page.toArgb()) painted++
        }
        return painted
    }

    private fun assertInside(weather: Weather, night: Boolean = false) {
        show(weather, night)
        // A few moments apart, as the drops and the clouds move.
        repeat(4) { moment ->
            compose.mainClock.advanceTimeBy(700)
            assertEquals("$weather at moment $moment", 0, paintedOutside())
        }
    }

    @Test
    fun `rain stays inside the scene`() = assertInside(Weather.RAIN)

    @Test
    fun `a storm stays inside the scene, by night too`() = assertInside(Weather.STORM, night = true)

    @Test
    fun `snow stays inside the scene`() = assertInside(Weather.SNOW)

    @Test
    fun `clouds stay inside the scene`() = assertInside(Weather.CLOUDY)
}
