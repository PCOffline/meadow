package dev.pcoffline.meadow.pictures.stories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import org.junit.Test

/** A friend code typed into the invite card stays when the card moves up or down People. */
class InviteCodeStory : Story("A typed friend code stays when the invite card moves") {
    private val alone = DemoGarden.robin.copy(approverIds = emptyList(), approveeIds = emptyList())
    private var people by mutableStateOf(People(outgoingInvites = listOf(DemoGarden.inviteFromRobin)))

    private fun toCode() = compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasSetTextAction())

    @Test
    fun story() {
        show(firstCaption = "An invite waits for an answer. The invite card is at the bottom.") { PeopleContent(alone, people) }
        step("A friend code typed into the card.") {
            toCode()
            compose.onNode(hasSetTextAction()).performTextInput("SAM123")
        }
        step("The waiting invite is cancelled. With nobody left, the card moves to the top, and the code stays.") {
            people = People()
            compose.waitForIdle()
            toCode()
        }
        step("An invite comes back. The card moves to the bottom, still with the code.") {
            people = People(outgoingInvites = listOf(DemoGarden.inviteFromRobin))
            compose.waitForIdle()
            toCode()
        }
    }
}
