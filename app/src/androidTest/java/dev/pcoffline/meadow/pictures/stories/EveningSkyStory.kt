package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.components.LocalSkyWeather
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Test

/** The garden's sky through the evening, with the Clock look: night falls at 19:00. */
class EveningSkyStory : Story("Night falls at 7 PM, with smooth clouds and a clean moon") {
    private var hour by mutableStateOf(18)

    @Test
    fun story() {
        show(firstCaption = "18:00 with Clock: still day, the sun low on the right.") {
            // The pinned hour also pins Clock's hour. The page draws its own background, as the
            // app's Scaffold does, so the night page is dark below the sky too.
            CompositionLocalProvider(LocalSkyHour provides hour, LocalSkyWeather provides Weather.PARTLY_CLOUDY) {
                MeadowTheme(GardenTheme.MEADOW, DarkMode.CLOCK) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        GardenContent(DemoGarden.robin, DemoGarden.garden)
                    }
                }
            }
        }
        step("19:00: night. The moon rises on the left, and the clouds are even, with no paler patches.") { hour = 19 }
        step("Midnight: the moon at the top of its arc. Its dark side shows only the glow behind it.") { hour = 0 }
    }
}
