package dev.pcoffline.meadow.pictures.stories

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import org.junit.Test

/** With a profile photo, the emoji is still a choice, and shows next to the name. */
class EmojiWithPhotoStory : Story("Pick an emoji when you have a profile photo") {
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun story() {
        // The photo's address doesn't load here, so its place shows empty.
        val robin = DemoGarden.robin.copy(avatar = DemoGarden.robin.avatar.copy(photoUrl = "https://example.invalid/robin.jpg"))
        show(firstCaption = "A profile with a photo.") { ProfileScreen(robin, onBack = {}, onSave = {}) }
        step("The emoji is still a choice. It shows next to your name, and there's no circle colour.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🌻"))).performScrollTo()
        }
        step("Picking 🌻 keeps the photo.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🌻"))).performClick()
        }
    }
}
