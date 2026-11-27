package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** A request to change a limit shows the whole limit, and what changes in it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RequestChangesTest : ScreenTest() {
    init {
        Locale.setDefault(Locale.UK)
    }

    private val pkg = "com.instagram.android"
    private val gone = TimeCap(listOf(7), 480, 720, 60)
    private val added = TimeCap(listOf(1, 2, 3, 4, 5), 1080, 1320, 30)

    private fun change(previous: AppLimit?) = LimitRequest(
        id = "r1", requesterId = "robin", requesterName = "Robin", type = RequestType.CHANGE_LIMIT,
        packageName = pkg, appName = "Instagram", minutes = 0, dailyMinutes = 45, weeklyMinutes = 180,
        message = "", status = RequestStatus.PENDING, approverIds = listOf("bee"), decidedByName = null,
        decisionNote = null, dayKey = "2026-11-27", createdAtMillis = System.currentTimeMillis(),
        caps = listOf(added), previous = previous,
    )

    private val before = AppLimit(pkg, "Instagram", 30, 180, caps = listOf(gone))

    @Test
    fun `a Bumblebee sees each part, with what changes said in words`() {
        show { RequestCard(change(before), myUid = "bee") }
        compose.onNodeWithText("A new limit for Instagram").assertExists()
        compose.onNodeWithContentDescription("Daily: from 30m to 45m").assertExists()
        compose.onNodeWithContentDescription("Weekly: 3h, no change").assertExists()
        compose.onNodeWithContentDescription("New cap: up to 30m 18:00–22:00 on weekdays").assertExists()
        compose.onNodeWithContentDescription("Removed cap: up to 1h 08:00–12:00 on Sun").assertExists()
        node(R.string.request_say_yes).assertExists()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `the card in a chat shows the same rows`() {
        show { RequestCard(change(before), myUid = "bee", compact = true) }
        compose.onNodeWithContentDescription("Daily: from 30m to 45m").assertExists()
        compose.onNodeWithContentDescription("Removed cap: up to 1h 08:00–12:00 on Sun").assertExists()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a change asked before the old limit was kept shows the new limit alone`() {
        show { RequestCard(change(null), myUid = "bee") }
        compose.onNodeWithContentDescription("Daily: 45m").assertExists()
        compose.onNodeWithContentDescription("Cap: up to 30m 18:00–22:00 on weekdays").assertExists()
        compose.onAllNodesWithContentDescription("New cap", substring = true).assertCountEquals(0)
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `my own open change shows only what changes, and Withdraw`() {
        show {
            Column {
                BackTopBar(text(R.string.limit_editor_existing), onBack = {})
                LimitEditor(
                    pkg,
                    LimitEditorState(existing = before, appName = "Instagram", bumblebeeCount = 1, pending = change(before), loaded = true),
                    onDone = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Daily: from 30m to 45m").performScrollTo().assertExists()
        compose.onAllNodesWithContentDescription("Weekly", substring = true).assertCountEquals(0)
        compose.onAllNodes(hasText("still waiting", substring = true)).assertCountEquals(0)
        node(R.string.request_withdraw).performScrollTo().assertExists()
        assertAccessible()
    }
}
