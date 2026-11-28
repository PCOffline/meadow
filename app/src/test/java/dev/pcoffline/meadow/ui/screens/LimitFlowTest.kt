@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.pcoffline.meadow.ui.screens

import dev.pcoffline.meadow.ui.clockText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsProperties
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.isSelectable
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
import dev.pcoffline.meadow.ui.limits.ShareLimitButton
import dev.pcoffline.meadow.ui.components.ShareCommitment
import androidx.compose.runtime.remember
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

    /** The editor with its top bar's Share, wired as LimitEditorScreen wires them. */
    private fun editorWithShare(saved: AppLimit?) = show {
        var unsaved by remember { mutableStateOf(false) }
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {}) { ShareLimitButton(saved, unsaved) }
            LimitEditor(pkg, LimitEditorState(existing = saved, appName = "Instagram", loaded = true), onDone = {}, onUnsaved = { unsaved = it })
        }
    }

    /** Opens the keypad of the daily limit, which reads [now], types [keys] and OK. */
    private fun typeDaily(now: String, vararg keys: String) {
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} $now").performScrollTo().performClick()
        keys.forEach { compose.onNode(hasText(it)).performClick() }
        node(R.string.action_ok).performClick()
    }

    /** Turns the daily limit, which reads [now], off on its keypad. */
    private fun noDaily(now: String = "30m") {
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} $now").performScrollTo().performClick()
        node(R.string.keypad_no_limit).performClick()
    }

    /** [day]'s column in the week's grid, which a screen reader reads with the day's actions. */
    private fun gridDay(day: Int) = compose.onNode(
        hasContentDescription(DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".", substring = true),
    )

    @Test
    fun `a saved limit can be shared at any time, as saved`() {
        val saved = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)
        editorWithShare(saved)
        try {
            compose.onNodeWithContentDescription(text(R.string.limit_share)).assertIsEnabled().performClick()
            assertEquals(saved, ShareCommitment.shared.value?.limit)
            assertTrue(ShareCommitment.shared.value!!.text.contains("Instagram"))
        } finally {
            ShareCommitment.close()
        }
        assertAccessible()
    }

    @Test
    fun `with unsaved changes, sharing waits until they're saved or undone`() {
        editorWithShare(AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0))
        val share = compose.onNodeWithContentDescription(text(R.string.limit_share))
        typeDaily("30m", "4", "5")
        share.assertIsNotEnabled()
        typeDaily("45m", "3", "0")
        share.assertIsEnabled()
    }
    @Test
    fun `a saved limit the editor puts in its own terms can still be shared`() {
        // Hours given as two windows that touch: the editor shows them as one.
        val hours = Schedule(mapOf(1 to listOf(TimeWindow(540, 720), TimeWindow(720, 1020))))
        editorWithShare(AppLimit(pkg, "Instagram", dailyMinutes = 0, weeklyMinutes = 120, schedule = hours))
        compose.onNodeWithContentDescription(text(R.string.limit_share)).assertIsEnabled()
    }

    @Test
    fun `a new limit has nothing saved to share`() {
        editorWithShare(null)
        compose.onNodeWithContentDescription(text(R.string.limit_share)).assertDoesNotExist()
    }

    @Test
    fun `a limit needs at least one part turned on, and says so`() {
        editor(LimitEditorState())
        node(R.string.limit_editor_plant).assertIsEnabled()
        node(R.string.limit_editor_pick_one).assertDoesNotExist()

        // Daily is on for a new limit; turning it off leaves nothing to plant.
        noDaily()
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
        typeDaily("30m", "4", "0")
        node(R.string.limit_editor_ask).performScrollTo().assertIsNotEnabled()
        // Tighter needs nobody's yes, so it can be saved meanwhile.
        typeDaily("40m", "2", "0")
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
    fun `typing a website's address offers to limit the website`() {
        val picked = mutableListOf<String>()
        show { AppPickerScreen(onBack = {}, onPick = { picked += it }) }
        compose.onNode(hasSetTextAction()).performTextInput("https://www.YouTube.com/watch?v=1")
        compose.waitUntil(5_000) { shows(text(R.string.picker_site, "youtube.com")) }
        node(R.string.picker_no_match, "https://www.YouTube.com/watch?v=1").assertDoesNotExist()
        node(R.string.picker_site, "youtube.com").performClick()
        assertEquals(listOf("site:youtube.com"), picked)
        assertAccessible()
    }

    @Test
    fun `a word that isn't an address offers no website`() {
        show { AppPickerScreen(onBack = {}, onPick = {}) }
        compose.onNode(hasSetTextAction()).performTextInput("youtube")
        compose.waitUntil(5_000) { shows(text(R.string.picker_no_match, "youtube")) }
        compose.onNodeWithText(text(R.string.picker_site_kind)).assertDoesNotExist()
    }

    @Test
    fun `a website's limit says which browsers it's counted in`() {
        show {
            LimitEditor("site:youtube.com", LimitEditorState(appName = "youtube.com", loaded = true), onDone = {})
        }
        node(R.string.limit_editor_site_browsers).assertExists()
        compose.onNodeWithText("youtube.com").assertExists()
    }

    @Test
    fun `an app's limit says nothing about browsers`() {
        editor(LimitEditorState())
        node(R.string.limit_editor_site_browsers).assertDoesNotExist()
    }

    @Test
    fun `the editor has no Share button, as the note that says it's saved shares the limit`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)))
        compose.onAllNodes(hasText("Share", substring = true)).assertCountEquals(0)
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
        typeDaily("30m", "4", "5")
        node(R.string.limit_editor_ask).performScrollTo().assertIsNotEnabled()
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        typeDaily("45m", "2", "0")
        node(R.string.action_save).performScrollTo().assertIsEnabled()
        assertAccessible()
    }

    @Test
    fun `while a change to the limit waits, the week shows the limit as it is and can't be changed`() {
        val change = pendingRequest().copy(type = RequestType.CHANGE_LIMIT, dailyMinutes = 45, previous = AppLimit(pkg, "Instagram", 30, 0))
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1, pending = change))
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 30m").performScrollTo().performClick()
        node(R.string.action_ok).assertDoesNotExist()
        node(R.string.week_as_now).performScrollTo().assertExists()
        // The waiting card lists what changes, and can take the question back.
        node(R.string.request_withdraw).assertExists()
        assertAccessible()
    }

    @Test
    fun `offline, a tighter limit still saves, but asking a Bumblebee says it needs a connection`() {
        Connectivity.set(false)
        try {
            editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1))
            typeDaily("30m", "2", "5")
            node(R.string.action_save).performScrollTo().assertIsEnabled()
            node(R.string.offline_needs_connection).assertDoesNotExist()

            // Up past where it was: a looser limit, which only a Bumblebee's yes allows.
            typeDaily("25m", "3", "5")
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
            typeDaily("30m", "3", "5")
            node(R.string.action_save).performScrollTo().assertIsEnabled()
            node(R.string.limit_editor_remove).performScrollTo().assertIsEnabled()
            node(R.string.offline_needs_connection).assertDoesNotExist()
        } finally {
            Connectivity.set(true)
        }
    }

    private fun chip(day: String) = compose.onNodeWithContentDescription(day)

    @Test
    fun `some days can have their own limit, and more time on them asks first`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1))
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        // An hour on Saturday is more than the everyday 30m.
        gridDay(6).performScrollTo().performCustomAccessibilityActionWithLabel(text(R.string.week_day_limit))
        listOf("1", "00").forEach { compose.onNode(hasText(it)).performClick() }
        node(R.string.action_ok).performClick()
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
        // Back to the everyday limit: nothing to ask.
        gridDay(6).performScrollTo().performCustomAccessibilityActionWithLabel(text(R.string.week_day_limit))
        node(R.string.keypad_reset, "30m").performClick()
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        // No limit on Sunday is looser too.
        gridDay(7).performScrollTo().performCustomAccessibilityActionWithLabel(text(R.string.week_day_limit))
        node(R.string.keypad_no_limit).performClick()
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
        assertAccessible()
    }
    @Test
    fun `a day's own limit shows under its name`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", 30, 0, dayMinutes = mapOf(6 to 120, 7 to 0))))
        // Saturday's two hours, and no limit on Sunday, in what a screen reader says.
        val saturday = gridDay(6).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(saturday, saturday.contains(text(R.string.week_a11y_daily, "2h")))
        val sunday = gridDay(7).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(sunday, sunday.contains(text(R.string.week_a11y_no_daily)))
    }
    @Test
    fun `a cap within hours is added from a day's actions, and only one that makes sense saves`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 60, weeklyMinutes = 0), bumblebeeCount = 1))
        gridDay(1).performScrollTo().performCustomAccessibilityActionWithLabel(text(R.string.week_add_cap))
        node(R.string.limit_editor_cap_title).assertExists()
        // Monday is picked; with no day, nothing saves.
        compose.onNodeWithContentDescription("Monday").performClick()
        node(R.string.fine_tune_pick_day).assertExists()
        node(R.string.action_ok).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Monday").performClick()
        node(R.string.action_ok).assertIsEnabled().performClick()
        val said = gridDay(1).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(said, said.contains(text(R.string.week_a11y_cap, "30m", text(R.string.schedule_window, clockText(600), clockText(960)))))
        // A new cap is stricter: no asking.
        node(R.string.limit_editor_loosens_title).assertDoesNotExist()
        assertAccessible()
    }
    @Test
    fun `removing a cap loosens the limit`() {
        val cap = TimeCap(listOf(1, 2, 3, 4, 5), 600, 960, 30)
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", 60, 0, caps = listOf(cap)), bumblebeeCount = 1))
        // A tap in the cap's hours picks it: 13:00 on Monday.
        gridDay(1).performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, height * 13f / 24f)) }
        compose.onNodeWithText(text(R.string.days_weekdays), substring = true, ignoreCase = true).assertExists()
        node(R.string.week_remove).performClick()
        node(R.string.limit_editor_loosens_title).performScrollTo().assertExists()
    }
    @Test
    fun `a cap alone is enough to plant a limit`() {
        editor(LimitEditorState())
        noDaily()
        node(R.string.limit_editor_plant).performScrollTo().assertIsNotEnabled()
        gridDay(1).performScrollTo().performCustomAccessibilityActionWithLabel(text(R.string.week_add_cap))
        node(R.string.action_ok).performClick()
        node(R.string.limit_editor_plant).performScrollTo().assertIsEnabled()
        node(R.string.limit_editor_pick_one).assertDoesNotExist()
    }
    @Test
    fun `the daily limit is typed as hours and minutes, and minutes over 59 count as hours`() {
        editor(LimitEditorState())
        typeDaily("30m", "1", "3", "0")
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 1h 30m").assertExists()
        typeDaily("1h 30m", "9", "0")
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 1h 30m").assertExists()
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
