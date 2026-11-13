package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Answering a request, and asking for more time: the dialogs behind the request card. */
@RunWith(AndroidJUnit4::class)
// The default screen: under Robolectric, a dialog with a text field never falls idle at
// w411dp-h891dp-xxhdpi. On a phone it does, and the e2e journeys open these dialogs there.
@Config(application = Application::class)
class RequestDialogsTest : ScreenTest() {
    private fun request(
        requesterId: String = "sam",
        status: RequestStatus = RequestStatus.PENDING,
        minutes: Int = 30,
        usedMinutes: Int? = 29,
        allowedMinutes: Int? = 30,
    ) = LimitRequest(
        id = "r1", requesterId = requesterId, requesterName = "Sam", type = RequestType.EXTRA_TIME,
        packageName = "com.instagram.android", appName = "Instagram", minutes = minutes, dailyMinutes = 0, weeklyMinutes = 0,
        message = "Finishing a message to my sister", status = status, approverIds = listOf("bee"),
        decidedByName = null, decisionNote = null, dayKey = "2026-10-13", createdAtMillis = System.currentTimeMillis() - 60_000,
        usedMinutes = usedMinutes, allowedMinutes = allowedMinutes,
    )

    private fun chip(minutes: Int) = compose.onNode(isSelectable() and hasText(app.resources.minutesText(minutes)))

    private fun chipCount(minutes: Int) =
        compose.onAllNodes(isSelectable() and hasText(app.resources.minutesText(minutes))).fetchSemanticsNodes().size

    @Test
    fun `approving offers the time asked for, or less`() {
        show { RequestCard(request(minutes = 30), myUid = "bee") }
        node(R.string.request_say_yes).performClick()

        node(R.string.request_decide_yes_title, "Sam").assertExists()
        node(R.string.request_decide_less).assertExists()
        // 5, 10 and 15 minutes are less than the 30 asked for; 60 is more, so it isn't offered.
        listOf(5, 10, 15, 30).forEach { assertEquals("$it minutes", 1, chipCount(it)) }
        assertEquals(0, chipCount(60))
        chip(30).assertIsSelected()
        chip(10).performClick()
        chip(10).assertIsSelected()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `denying asks only for a kind word`() {
        show { RequestCard(request(), myUid = "bee") }
        node(R.string.request_not_today).performClick()

        node(R.string.request_decide_no_title).assertExists()
        node(R.string.request_decide_less).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Not tonight")
        compose.onNode(hasSetTextAction()).assertTextContains("Not tonight")
        compose.onNode(hasText(text(R.string.action_send)) and hasClickAction()).assertIsEnabled()

        // Back leaves the request as it was, waiting for an answer.
        node(R.string.action_back).performClick()
        node(R.string.request_decide_no_title).assertDoesNotExist()
        node(R.string.request_say_yes).assertExists()
    }

    @Test
    fun `the day's use shows without an allowance too`() {
        show { RequestCard(request(usedMinutes = 45, allowedMinutes = null), myUid = "bee") }
        node(R.string.request_usage_no_allowance, app.resources.minutesText(45)).assertExists()
    }

    @Test
    fun `an expired question can be asked again, as it was`() {
        AppGraph.store.setLimits(listOf(AppLimit("com.instagram.android", "Instagram", 30, 0)))
        show { RequestCard(request(requesterId = "sam", status = RequestStatus.EXPIRED, minutes = 30), myUid = "sam") }
        node(R.string.request_ask_again).performClick()

        node(R.string.ask_title).assertExists()
        chip(30).assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("Finishing a message to my sister")
        // The app is known, so the dialog doesn't offer to pick another.
        compose.onNodeWithText("Instagram").assertDoesNotExist()
    }

    @Test
    fun `asking from the garden picks among the limits`() {
        AppGraph.store.setLimits(listOf(AppLimit("demo.reader", "Reader", 30, 0), AppLimit("demo.tiktok", "TikTok", 20, 0)))
        show { AskForTimeDialog(packageName = null, onDismiss = {}) }

        // The first limit is picked until another is.
        compose.onNode(isSelectable() and hasText("Reader")).assertIsSelected()
        compose.onNode(isSelectable() and hasText("TikTok")).performClick()
        compose.onNode(isSelectable() and hasText("TikTok")).assertIsSelected()
        chip(15).assertIsSelected()
        chip(60).performClick()
        chip(60).assertIsSelected()
        compose.onNode(hasText(text(R.string.action_send)) and hasClickAction()).assertIsEnabled()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `with no limits there is nothing to ask about`() {
        AppGraph.store.setLimits(emptyList())
        var closed = false
        show { AskForTimeDialog(packageName = null, onDismiss = { closed = true }) }

        node(R.string.ask_no_limits).assertExists()
        compose.onNode(hasText(text(R.string.action_send)) and hasClickAction()).assertIsNotEnabled()
        node(R.string.action_cancel).performClick()
        assertEquals(true, closed)
    }
}
