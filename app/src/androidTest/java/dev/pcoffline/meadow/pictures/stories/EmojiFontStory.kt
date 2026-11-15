package dev.pcoffline.meadow.pictures.stories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.home.GardenContent
import org.junit.Test

/** Emoji drawn with Meadow's own font, on Android 15 and later as on older phones. */
class EmojiFontStory : Story("Emoji look the same on Android 15 and later") {
    private var people by mutableStateOf(false)

    @Test
    fun story() {
        show(firstCaption = "The garden: its emoji use Meadow's font.") {
            if (people) PeopleContent(DemoGarden.robin, DemoGarden.people) else GardenContent(DemoGarden.robin, DemoGarden.garden)
        }
        step("People: each person's emoji uses Meadow's font too.") { people = true }
    }
}
