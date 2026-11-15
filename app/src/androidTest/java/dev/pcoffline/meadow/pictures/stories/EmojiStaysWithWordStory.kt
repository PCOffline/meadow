package dev.pcoffline.meadow.pictures.stories

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.pcoffline.meadow.showcase.Scenarios
import org.junit.Test

/** A title that ends with an emoji keeps the emoji on the line of its last word. */
class EmojiStaysWithWordStory : Story("An emoji at the end of a title stays with its last word") {
    private var scale by mutableFloatStateOf(1f)

    @Test
    fun story() {
        val emergency = Scenarios.all.first { it.screen == "Resting app" && it.name == "Emergency unlock" }
        show(firstCaption = "The title wraps, and 🚨 stays on the line with \"on\".") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
                emergency.content()
            }
        }
        step("With text at 1.3 times its size, the title wraps sooner, and 🚨 still stays with \"on\".") {
            compose.runOnUiThread { scale = 1.3f }
        }
    }
}
