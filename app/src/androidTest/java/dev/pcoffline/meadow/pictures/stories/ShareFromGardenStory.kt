package dev.pcoffline.meadow.pictures.stories

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.ShareCommitmentHost
import dev.pcoffline.meadow.ui.home.GardenContent
import org.junit.Test

/** A long press on a flower card shares its saved limit. */
class ShareFromGardenStory : Story("Share a limit from its card in the garden") {
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun story() {
        val first = DemoGarden.garden.budgets.first().limit.appName
        show(firstCaption = "The garden, with a card for each limit.") {
            GardenContent(DemoGarden.robin, DemoGarden.garden)
            ShareCommitmentHost()
        }
        step("A long press on $first's card shows its choices.") {
            compose.onNode(hasClickAction() and hasText(first, substring = true)).performTouchInput { longClick() }
        }
        step("Share this limit opens the share sheet with the limit as saved.") {
            compose.onNodeWithText(text(R.string.limit_share)).performClick()
        }
    }
}
