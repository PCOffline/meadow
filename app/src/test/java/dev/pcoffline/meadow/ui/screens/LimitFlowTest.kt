package dev.pcoffline.meadow.ui.screens

import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.minutesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Planting and tending a limit, and how the garden and tab bar read. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LimitFlowTest : ScreenTest() {
    private val pkg = "com.instagram.android"

    // With the top bar, as LimitEditorScreen shows it: the bar carries the screen's heading.
    private fun editor(state: LimitEditorState) = show {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(pkg, state.copy(appName = "Instagram", loaded = true), onDone = {})
        }
    }

    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun pendingRequest() = LimitRequest(
        id = "r1", requesterId = "me", requesterName = "Me", type = RequestType.EXTRA_TIME,
        packageName = pkg, appName = "Instagram", minutes = 15, dailyMinutes = 0, weeklyMinutes = 0,
        message = "", status = RequestStatus.PENDING, approverIds = listOf("bee"), decidedByName = null,
        decisionNote = null, dayKey = "2026-10-25", createdAtMillis = System.currentTimeMillis(),
    )

    @Test
    fun `a limit needs at least one part turned on, and says so`() {
        editor(LimitEditorState())
        node(R.string.limit_editor_plant).assertIsEnabled()
        node(R.string.limit_editor_pick_one).assertDoesNotExist()

        // Daily is on for a new limit; turning it off leaves nothing to plant.
        compose.onNodeWithText(text(R.string.limit_editor_daily)).performClick()
        node(R.string.limit_editor_plant).performScrollTo().assertIsNotEnabled()
        node(R.string.limit_editor_pick_one).performScrollTo().assertExists()
        assertAccessible()
    }

    @Test
    fun `a new limit says loosening it will need a Bumblebee, when there is one`() {
        editor(LimitEditorState(bumblebeeCount = 1))
        node(R.string.limit_editor_locked_hint).performScrollTo().assertExists()
    }

    @Test
    fun `with no Bumblebees, a new limit makes no promise about loosening`() {
        editor(LimitEditorState())
        node(R.string.limit_editor_locked_hint).assertDoesNotExist()
    }

    @Test
    fun `a planted limit doesn't repeat the loosening note`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1))
        node(R.string.limit_editor_locked_hint).assertDoesNotExist()
    }

    @Test
    fun `searching for an app that isn't there says so`() {
        show { AppPickerScreen(onBack = {}, onPick = {}) }
        compose.onNode(hasSetTextAction()).performTextInput("zzqx")
        // The app list loads in the background.
        compose.waitUntil(5_000) { shows(text(R.string.picker_no_match, "zzqx")) }
    }

    @Test
    fun `a tap steps once, and holding keeps stepping`() {
        editor(LimitEditorState())
        val more = compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))
        more[0].performClick()
        compose.onNodeWithText(app.resources.minutesText(35)).assertExists()

        compose.mainClock.autoAdvance = false
        more[0].performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        more[0].performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Held for well past the start delay: several more steps than the one a tap gives.
        assertTrue((50..16 * 60 step 5).any { shows(app.resources.minutesText(it)) })
    }

    @Test
    fun `the stepper's buttons stay put as the value grows from whole hours to hours and minutes`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 0, weeklyMinutes = 5 * 60)))
        val less = compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0]
        val more = compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0]
        val before = less.fetchSemanticsNode().boundsInRoot to more.fetchSemanticsNode().boundsInRoot
        more.performClick()
        compose.onNodeWithText(app.resources.minutesText(5 * 60 + 30)).assertExists()
        assertEquals(before, less.fetchSemanticsNode().boundsInRoot to more.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `a question already waiting about the app is shown, and blocks asking another`() {
        editor(
            LimitEditorState(
                existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0),
                bumblebeeCount = 1,
                pending = pendingRequest(),
            ),
        )
        node(R.string.limit_editor_pending_title).performScrollTo().assertExists()
        node(R.string.request_withdraw).assertExists()
        // Removing would be another question about the same app.
        node(R.string.limit_editor_ask_remove).performScrollTo().assertIsNotEnabled()

        // Loosening would be too; tightening needs no question, so it stays possible.
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0].performClick()
        node(R.string.limit_editor_ask).performScrollTo().assertIsNotEnabled()
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0].performClick()
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0].performClick()
        node(R.string.action_save).performScrollTo().assertIsEnabled()
        assertAccessible()
    }

    @Test
    fun `offline, a tighter limit still saves, but asking a Bumblebee says it needs a connection`() {
        Connectivity.set(false)
        try {
            editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1))
            compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0].performClick()
            node(R.string.action_save).performScrollTo().assertIsEnabled()
            node(R.string.offline_needs_connection).assertDoesNotExist()

            // Up past where it was: a looser limit, which only a Bumblebee's yes allows.
            repeat(2) { compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0].performClick() }
            node(R.string.offline_needs_connection).performScrollTo().assertExists()
            node(R.string.limit_editor_ask).performScrollTo().assertIsNotEnabled()
        } finally {
            Connectivity.set(true)
        }
    }

    @Test
    fun `offline with no Bumblebees, a limit can be loosened and removed`() {
        Connectivity.set(false)
        try {
            editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)))
            compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0].performClick()
            node(R.string.action_save).performScrollTo().assertIsEnabled()
            node(R.string.limit_editor_remove).performScrollTo().assertIsEnabled()
            node(R.string.offline_needs_connection).assertDoesNotExist()
        } finally {
            Connectivity.set(true)
        }
    }

    @Test
    fun `a limit set only by hours says when it closes`() {
        val hoursOnly = AppBudget(
            AppLimit("demo.reader", "Reader", 0, 0, Schedule.everyDay(TimeWindow(9 * 60, 22 * 60))),
            usedTodayMs = 0, usedWeekMs = 0, bonusTodayMinutes = 0, bonusWeekMinutes = 0,
            untilScheduleChangeMs = 2 * 60 * 60_000L,
        )
        show { GardenContent(DemoGarden.robin, GardenState(budgets = listOf(hoursOnly), loaded = true)) }
        compose.onNodeWithText(text(R.string.garden_tile_open_until, ""), substring = true).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.garden_tile_left_today, ""), substring = true).assertDoesNotExist()
    }

    @Test
    fun `each tab is named once`() {
        show { MeadowTabBar(Tab.GARDEN, Badges(), onSelect = {}) }
        compose.onAllNodesWithContentDescription(text(R.string.tab_garden)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.tab_garden)).assertExists()
    }
}
