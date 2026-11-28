package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.limits.WeekCard
import dev.pcoffline.meadow.ui.limits.WeekLimit
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The week card (#216): the daily and weekly limits on the keypad, a day's actions, Allow all,
 * Fine-tune, picking and drawing on the grid. Dialogs open here, so this class has no qualifiers.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WeekCardTest : ScreenTest() {
    private val weekdays = Schedule((1..5).associateWith { listOf(TimeWindow(600, 1320)) })
    private var limit by mutableStateOf(WeekLimit(weekdays, emptyList(), 30, 180, emptyMap()))
    private val snackbar = SnackbarHostState()

    private fun card(enabled: Boolean = true, waiting: Boolean = false, step: Int = 15) =
        compose.setContent {
            MeadowTheme(GardenTheme.MEADOW, DarkMode.LIGHT) {
                AppBackground {
                    CompositionLocalProvider(LocalSnackbar provides snackbar) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            WeekCard(limit, DayOfWeek.MONDAY, step, enabled, onChange = { limit = it }, onStep = {}, onHelp = {}, waiting = waiting)
                        }
                    }
                }
            }
        }

    private fun dayName(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** The grid's node for [day], which a screen reader reads, with the day's actions. */
    private fun day(day: Int): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("the grid's ${dayName(day)}") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(dayName(day) + ".") } == true
        },
    )

    private fun SemanticsNodeInteraction.act(@androidx.annotation.StringRes label: Int) {
        val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.firstOrNull { it.label == text(label) } ?: throw AssertionError("No action \"${text(label)}\" in ${actions.map { it.label }}")
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

    private fun type(vararg keys: String) = keys.forEach { compose.onNode(hasText(it)).performClick() }

    @Test
    fun `the daily and weekly limits are typed on the keypad, and No limit turns one off`() {
        card()
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 30m").performClick()
        type("4", "5")
        node(R.string.action_ok).performClick()
        assertEquals(45, limit.daily)
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_weekly)} 3h").performClick()
        node(R.string.keypad_no_limit).performClick()
        assertEquals(0, limit.weekly)
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_weekly)} ${text(R.string.week_off)}").assertExists()
    }

    @Test
    fun `a whole day is rested or allowed with one action`() {
        card()
        day(6).act(R.string.week_day_resting)
        assertEquals(emptyList<TimeWindow>(), limit.schedule!!.days[6])
        day(1).act(R.string.week_day_all)
        assertNull(limit.schedule!!.days[1])
    }

    @Test
    fun `a tap on a day's name shows its actions, and the whole day is set from there`() {
        card()
        val short = DayOfWeek.SATURDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        compose.onNode(hasText(short)).performTouchInput { click() }
        compose.onNode(hasText(dayName(6))).assertExists()
        assertAccessible()
        node(R.string.week_day_resting).performClick()
        assertEquals(emptyList<TimeWindow>(), limit.schedule!!.days[6])
        node(R.string.week_day_all).performClick()
        assertNull(limit.schedule!!.days[6])
    }

    @Test
    fun `a day's own limit is typed, reset to the daily limit, or set to none`() {
        card()
        day(6).act(R.string.week_day_limit)
        node(R.string.keypad_day_title, dayName(6)).assertExists()
        type("1", "00")
        node(R.string.action_ok).performClick()
        assertEquals(60, limit.dayMinutes[6])
        day(6).act(R.string.week_day_limit)
        node(R.string.keypad_reset, "30m").performClick()
        assertNull(limit.dayMinutes[6])
        day(6).act(R.string.week_day_limit)
        node(R.string.keypad_no_limit).performClick()
        assertEquals(0, limit.dayMinutes[6])
    }

    @Test
    fun `Allow all clears every stretch, and Undo puts them back`() {
        card()
        compose.onNodeWithContentDescription(text(R.string.week_allow_all)).performClick()
        assertNull(limit.schedule)
        compose.waitUntil(5_000) { snackbar.currentSnackbarData != null }
        assertEquals(text(R.string.week_allow_all_done), snackbar.currentSnackbarData!!.visuals.message)
        compose.runOnIdle { snackbar.currentSnackbarData!!.performAction() }
        compose.waitUntil(5_000) { limit.schedule == weekdays }
    }

    @Test
    fun `Fine-tune adds hours on the days picked, without a drag`() {
        limit = limit.copy(schedule = null)
        card()
        day(6).act(R.string.week_add_hours)
        node(R.string.fine_tune_title).assertExists()
        assertAccessible()
        node(R.string.action_ok).performClick()
        compose.waitForIdle()
        assertEquals(listOf(TimeWindow(600, 960)), limit.schedule!!.days[6])
        assertNull("days not drawn on stay allowed", limit.schedule!!.days[5])
    }

    @Test
    fun `a cap is added on a day, and its amount must be shorter than its hours`() {
        card()
        day(3).act(R.string.week_add_cap)
        node(R.string.limit_editor_cap_title).assertExists()
        node(R.string.action_ok).performClick()
        compose.waitForIdle()
        assertEquals(listOf(TimeCap(listOf(3), 600, 960, 30)), limit.caps)
    }

    @Test
    fun `a tap picks a stretch, which can be removed, and the day goes back to allowed all day`() {
        card()
        // Monday's stretch is 10:00 to 22:00: the middle of the day is in it.
        day(1).performTouchInput { click(center) }
        compose.onNode(hasText("Mon 10:00 – Mon 22:00", substring = true)).assertExists()
        assertAccessible()
        node(R.string.week_remove).performClick()
        assertNull(limit.schedule!!.days[1])
        assertEquals(listOf(TimeWindow(600, 1320)), limit.schedule!!.days[2])
    }

    @Test
    fun `a long press and a drag draw hours, in steps`() {
        limit = limit.copy(schedule = Schedule(mapOf(2 to emptyList())))
        card(step = 15)
        day(2).performTouchInput {
            // The whole day shows: 10:00 is 10/24 of the way down, 14:00 is 14/24.
            val x = center.x
            down(androidx.compose.ui.geometry.Offset(x, height * 10f / 24f))
            advanceEventTime(1_000)
            moveTo(androidx.compose.ui.geometry.Offset(x, height * 12f / 24f))
            moveTo(androidx.compose.ui.geometry.Offset(x, height * 14f / 24f))
            up()
        }
        compose.waitForIdle()
        val windows = limit.schedule!!.days[2]!!
        assertEquals(1, windows.size)
        assertTrue("$windows starts near 10:00", windows.single().from in 585..615)
        assertTrue("$windows ends near 14:00", windows.single().to in 825..855)
        assertTrue("$windows moves in 15-minute steps", windows.single().from % 15 == 0 && windows.single().to % 15 == 0)
    }

    @Test
    fun `while waiting for an answer, nothing changes, and each day says why`() {
        card(enabled = false, waiting = true)
        val node = day(1).fetchSemanticsNode()
        assertTrue(node.config.getOrNull(SemanticsProperties.ContentDescription)!!.single().endsWith(text(R.string.week_a11y_waiting)))
        assertTrue(node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 30m").performClick()
        node(R.string.action_ok).assertDoesNotExist()
    }

    @Test
    fun `a screen reader hears each day's hours, caps and limit`() {
        limit = limit.copy(caps = listOf(TimeCap(listOf(1), 840, 1320, 30)), dayMinutes = mapOf(1 to 60))
        card()
        val said = day(1).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
        assertTrue(said, said.startsWith(dayName(1) + "."))
        assertTrue(said, said.contains(text(R.string.week_a11y_daily, "1h")))
        assertTrue(said, said.contains("30m"))
    }

    @Test
    fun `the card is readable and reachable for everyone, at rest and with the keypad open`() {
        card()
        assertAccessible()
        day(6).act(R.string.week_day_limit)
        assertAccessible()
    }
}
