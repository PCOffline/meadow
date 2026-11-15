package dev.pcoffline.meadow.pictures.stories

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyWeather
import dev.pcoffline.meadow.ui.home.GardenContent
import org.junit.Test

/** The garden's header in the rain: the drops stay in the scene, off the page below. */
class RainInSceneStory : Story("Rain and snow stay inside the garden's scene") {
    private var weather by mutableStateOf(Weather.RAIN)

    @Test
    fun story() {
        show(firstCaption = "Rain: the drops stop at the bottom of the scene. The page below stays clear.") {
            CompositionLocalProvider(LocalSkyWeather provides weather) {
                GardenContent(DemoGarden.robin, DemoGarden.garden)
            }
        }
        step("A storm: heavier rain, still only inside the scene.") { weather = Weather.STORM }
        step("Snow: the flakes stay inside the scene too.") { weather = Weather.SNOW }
    }
}
