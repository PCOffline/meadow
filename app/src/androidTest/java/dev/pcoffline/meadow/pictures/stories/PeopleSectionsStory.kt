package dev.pcoffline.meadow.pictures.stories

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import org.junit.Test

/** People: names without repeated roles, and a plus button by each section to add someone. */
class PeopleSectionsStory : Story("Add people from a plus button by each section") {
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun story() {
        val people = DemoGarden.people.copy(incomingRequests = emptyList(), outgoingRequests = emptyList(), incomingInvites = emptyList(), outgoingInvites = emptyList())
        show(firstCaption = "Each card shows a name and a bio. The section names say who looks after whom.") {
            PeopleContent(DemoGarden.robin, People(bumblebees = people.bumblebees.mapIndexed { i, bee -> if (i == 0) bee.copy(bio = "") else bee }, lookingAfter = people.lookingAfter, chats = people.chats))
        }
        step("Your answers as a Bumblebee is under the gardens that you look after.") {
            compose.onNodeWithText(text(R.string.bee_stats_open)).performScrollTo()
        }
        step("The plus button by Your Bumblebees opens the invite.") {
            compose.onNodeWithContentDescription(text(R.string.people_invite_more)).performScrollTo().performClick()
        }
        step("Closed again, the list is as before.") {
            Espresso.pressBack()
        }
        step("The plus button by Gardens you look after shows your friend code.") {
            compose.onNodeWithContentDescription(text(R.string.circle_join_title)).performScrollTo().performClick()
        }
    }
}
