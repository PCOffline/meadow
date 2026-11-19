package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A Bumblebee answers a request: yes with less time, or no with a note. On the default screen:
 * with a tall phone's qualifiers, Robolectric never settles with these dialogs open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RequestDecideTest : ScreenTest() {
    private fun request() = LimitRequest(
        id = "r1", requesterId = "sam", requesterName = "Sam", type = RequestType.EXTRA_TIME,
        packageName = "com.instagram.android", appName = "Instagram", minutes = 30, dailyMinutes = 0, weeklyMinutes = 0,
        message = "Finishing a message to my sister", status = RequestStatus.PENDING, approverIds = listOf("bee", "robin"),
        decidedByName = null, decisionNote = null, dayKey = "2026-10-13", createdAtMillis = System.currentTimeMillis() - 60_000, usedMinutes = 29, allowedMinutes = 30,
    )

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `answering opens a dialog with less time to give, and a failed answer says so on the card`() {
        show { RequestCard(request(), myUid = "bee") }
        assertAccessible(requireHeading = false)
        node(R.string.request_say_yes).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(text(R.string.request_decide_yes_title, "Sam")).assertExists()
        inDialog(app.resources.minutesText(15)).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(text(R.string.action_back)).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(text(R.string.request_decide_yes_title, "Sam")).assertDoesNotExist()

        node(R.string.request_not_today).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(text(R.string.request_decide_no_title)).assertExists()
        // These tests have no backend, so sending fails, and the card says so.
        inDialog(text(R.string.action_send)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        node(R.string.request_say_yes).performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodes(hasText(text(R.string.request_say_yes)) and hasAnyAncestor(isDialog())).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun `a change to a limit is answered without a choice of time`() {
        show { RequestCard(request().copy(type = RequestType.CHANGE_LIMIT, minutes = 0, dailyMinutes = 20, usedMinutes = null), myUid = "bee") }
        node(R.string.request_say_yes).performSemanticsAction(SemanticsActions.OnClick)
        inDialog(text(R.string.request_decide_less)).assertDoesNotExist()
        inDialog(text(R.string.action_back)).performSemanticsAction(SemanticsActions.OnClick)
    }

}
