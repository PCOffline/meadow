package dev.pcoffline.meadow.ui.screens

import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import dev.pcoffline.meadow.data.TimeCap
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
    fun `until it's known whether a question is open, asking waits but the limit shows`() {
        val limit = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)
        editor(LimitEditorState(existing = limit, bumblebeeCount = 1, pendingKnown = false))
        node(R.string.limit_editor_ask_remove).performScrollTo().assertIsNotEnabled()
        // Looser: asking a Bumblebee waits too.
        repeat(2) { compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0].performClick() }
        node(R.string.limit_editor_ask).performScrollTo().assertIsNotEnabled()
        // Tighter needs nobody's yes, so it can be saved meanwhile.
        repeat(4) { compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0].performClick() }
        node(R.string.action_save).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `with no Bumblebees, nothing waits on a question that can't be open`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), pendingKnown = false))
        node(R.string.limit_editor_remove).performScrollTo().assertIsEnabled()
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

    private fun chip(day: String) = compose.onNodeWithContentDescription(day)

    @Test
    fun `some days can have their own limit, picked together, and more time on them asks first`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1))
        node(R.string.limit_editor_days_add).performScrollTo().performClick()
        // No days picked yet: nothing changes.
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        chip("Saturday").performScrollTo().performClick()
        chip("Sunday").performScrollTo().performClick()
        // An hour on weekends is more than the everyday 30m.
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
        node(R.string.limit_editor_days_no_limit).performScrollTo().performClick()
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
        // Every day is taken once picked, so another group can't pick the weekend again.
        node(R.string.limit_editor_days_add).performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("Saturday")[1].assertIsNotEnabled()
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_days_remove))[0].performClick()
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_days_remove))[0].performClick()
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `a day's own limit shows with its days picked`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", 30, 0, dayMinutes = mapOf(6 to 120, 7 to 120))))
        chip("Saturday").performScrollTo().assertIsSelected()
        chip("Sunday").assertIsSelected()
        chip("Monday").assertIsNotSelected()
        // Their two hours, beside the everyday presets' "2h".
        compose.onAllNodesWithText("2h").assertCountEquals(2)
    }

    @Test
    fun `a cap within hours is added from a dialog that only saves one that makes sense`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 60, weeklyMinutes = 0), bumblebeeCount = 1))
        node(R.string.limit_editor_cap_add).performScrollTo().performClick()
        node(R.string.limit_editor_cap_title).assertExists()
        // Weekdays, 10:00–16:00 and 30 minutes to start with.
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday").forEach { chip(it).assertIsSelected().performClick() }
        node(R.string.limit_editor_cap_pick_days).assertExists()
        node(R.string.action_ok).assertIsNotEnabled()
        chip("Monday").performClick()
        node(R.string.action_ok).assertIsEnabled().performClick()
        compose.onNodeWithText("Up to 30m", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.days_some, "Mon"), substring = true, ignoreCase = true).assertExists()
        // A new cap is stricter: no asking.
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `removing a cap loosens the limit`() {
        val cap = TimeCap(listOf(1, 2, 3, 4, 5), 600, 960, 30)
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", 60, 0, caps = listOf(cap)), bumblebeeCount = 1))
        compose.onNodeWithText("Up to 30m", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.days_weekdays), substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithContentDescription(text(R.string.limit_editor_cap_remove)).performClick()
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
    }

    @Test
    fun `a cap alone is enough to plant a limit`() {
        editor(LimitEditorState())
        compose.onNodeWithText(text(R.string.limit_editor_daily)).performClick()
        node(R.string.limit_editor_plant).performScrollTo().assertIsNotEnabled()
        node(R.string.limit_editor_cap_add).performScrollTo().performClick()
        node(R.string.action_ok).performClick()
        node(R.string.limit_editor_plant).performScrollTo().assertIsEnabled()
        node(R.string.limit_editor_pick_one).assertDoesNotExist()
    }

    @Test
    fun `the daily limit can be picked on a clock`() {
        editor(LimitEditorState())
        val pick = text(R.string.limit_editor_pick_time)
        compose.onAllNodes(SemanticsMatcher("opens a clock") { it.config.getOrNull(SemanticsActions.OnClick)?.label == pick })
            .onFirst().performClick()
        // The dialog is titled with what it sets, and keeps the value when confirmed.
        compose.onAllNodesWithText(text(R.string.limit_editor_daily)).assertCountEquals(2)
        node(R.string.action_ok).performClick()
        compose.onAllNodesWithText(text(R.string.limit_editor_daily)).assertCountEquals(1)
        compose.onAllNodesWithText("30m").assertCountEquals(2)
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

    @Test
    fun `three tabs, with everything waiting on people counted on People`() {
        show { MeadowTabBar(Tab.PEOPLE, Badges(requests = 2, invites = 1, chats = 3), onSelect = {}) }
        assertEquals(listOf(Tab.GARDEN, Tab.PEOPLE, Tab.STATS), Tab.entries)
        compose.onNodeWithText("6", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)
    }
}
