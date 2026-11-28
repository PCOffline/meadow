package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Asking, answering and editing: request cards and the allowed-hours editor. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorsTest : ScreenTest() {
    private fun request(
        requesterId: String = "sam",
        status: RequestStatus = RequestStatus.PENDING,
        decidedByName: String? = null,
        decisionNote: String? = null,
        myDecision: String? = null,
    ) = LimitRequest(
        id = "r1", requesterId = requesterId, requesterName = "Sam", type = RequestType.EXTRA_TIME,
        packageName = "com.instagram.android", appName = "Instagram", minutes = 30, dailyMinutes = 0, weeklyMinutes = 0,
        message = "Finishing a message to my sister", status = status, approverIds = listOf("bee", "robin"),
        decidedByName = decidedByName, decisionNote = decisionNote, dayKey = "2026-10-13",
        createdAtMillis = System.currentTimeMillis() - 60_000, myDecision = myDecision, usedMinutes = 29, allowedMinutes = 30,
    )

    private fun countText(value: String, substring: Boolean = false) =
        compose.onAllNodesWithText(value, substring).fetchSemanticsNodes().size

    @Test
    fun `a Bumblebee sees who asks, why, how the day stood, and can answer it`() {
        show { RequestCard(request(), myUid = "bee") }
        node(R.string.request_they_ask, "Sam").assertExists()
        compose.onNodeWithText("Finishing a message to my sister", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.request_status_waiting)).assertExists()
        compose.onNodeWithText(text(R.string.request_usage, app.resources.minutesText(29), app.resources.minutesText(30))).assertExists()
        node(R.string.request_withdraw).assertDoesNotExist()
        assertAccessible(requireHeading = false)

        // Answering opens a dialog: RequestDecideTest.
        node(R.string.request_say_yes).assertHasClickAction()
        node(R.string.request_not_today).assertHasClickAction()
    }

    @Test
    fun `an ask nobody answered in time can be asked again, by the asker only`() {
        var mine by mutableStateOf(true)
        var status by mutableStateOf(RequestStatus.EXPIRED)
        show { RequestCard(request(status = status), myUid = if (mine) "sam" else "bee") }
        node(R.string.request_ask_again).assertHasClickAction()
        assertAccessible(requireHeading = false)
        mine = false
        node(R.string.request_ask_again).assertDoesNotExist()
        mine = true
        status = RequestStatus.PENDING
        node(R.string.request_ask_again).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee never learns which other Bumblebee answered`() {
        // The backend may know who decided; another Bumblebee's card must not show it.
        show { RequestCard(request(status = RequestStatus.APPROVED, decidedByName = "Robin", decisionNote = "Go for it"), myUid = "bee") }
        node(R.string.request_answered_by_other).assertExists()
        assertEquals(0, countText("Robin", substring = true))
        assertEquals(0, countText("Go for it", substring = true))
        node(R.string.request_say_yes).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee who answered sees their own answer`() {
        show { RequestCard(request(status = RequestStatus.DENIED, decidedByName = "Bee", myDecision = "no"), myUid = "bee") }
        node(R.string.request_you_said_no).assertExists()
        // "Not today" is still its status, but no longer a button.
        compose.onNode(hasText(text(R.string.request_not_today)) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `the asker sees who answered and their note, and can withdraw while waiting`() {
        show { RequestCard(request(status = RequestStatus.APPROVED, decidedByName = "Bee", decisionNote = "Enjoy"), myUid = "sam") }
        node(R.string.request_you_asked).assertExists()
        compose.onNodeWithText(text(R.string.request_decided_with_note, text(R.string.request_decided_yes, "Bee"), "Enjoy")).assertExists()
        node(R.string.request_withdraw).assertDoesNotExist()
        node(R.string.request_say_yes).assertDoesNotExist()
    }

    @Test
    fun `the asker sees a no without a note, and nothing more when nobody's name came`() {
        var decider by mutableStateOf<String?>("Bee")
        show { RequestCard(request(status = RequestStatus.DENIED, decidedByName = decider, decisionNote = " "), myUid = "sam") }
        compose.onNodeWithText(text(R.string.request_decided_no, "Bee")).assertExists()
        compose.onNodeWithText(text(R.string.request_status_no)).assertExists()
        decider = null
        compose.onNodeWithText(text(R.string.request_decided_no, "Bee")).assertDoesNotExist()
    }

    @Test
    fun `a Bumblebee who said yes sees it`() {
        show { RequestCard(request(status = RequestStatus.APPROVED, decidedByName = "Bee", myDecision = "yes"), myUid = "bee") }
        node(R.string.request_you_said_yes).assertExists()
    }

    @Test
    fun `withdrawn and expired asks say so, and a day without an allowance shows only the time used`() {
        var status by mutableStateOf(RequestStatus.CANCELLED)
        var allowed by mutableStateOf<Int?>(null)
        var used by mutableStateOf<Int?>(29)
        show { RequestCard(request(status = status).copy(allowedMinutes = allowed, usedMinutes = used), myUid = "bee", compact = true) }
        compose.onNodeWithText(text(R.string.request_status_withdrawn)).assertExists()
        compose.onNodeWithText(text(R.string.request_usage_no_allowance, app.resources.minutesText(29))).assertExists()
        status = RequestStatus.EXPIRED
        allowed = 0
        compose.onNodeWithText(text(R.string.request_status_expired)).assertExists()
        compose.onNodeWithText(text(R.string.request_usage_no_allowance, app.resources.minutesText(29))).assertExists()
        used = null
        compose.onNodeWithText(text(R.string.request_usage_no_allowance, app.resources.minutesText(29))).assertDoesNotExist()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a request about strict mode has no app`() {
        show { RequestCard(request().copy(type = RequestType.DISABLE_STRICT_MODE, packageName = null, appName = null), myUid = "bee") }
        compose.onNodeWithText(text(R.string.request_disable_strict_mode), substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun `a waiting request of your own can be withdrawn, not answered`() {
        show { RequestCard(request(), myUid = "sam") }
        node(R.string.request_withdraw).assertExists()
        node(R.string.request_say_yes).assertDoesNotExist()
        node(R.string.request_not_today).assertDoesNotExist()
    }
}
