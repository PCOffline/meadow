package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** What the week card's action bars, keypad and Fine-tune do (#216). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WeekCardActionsTest : ScreenTest() {
    private val weekdays = Schedule((1..5).associateWith { listOf(TimeWindow(600, 1320)) })
    private var limit by mutableStateOf(WeekLimit(weekdays, emptyList(), 30, 180, emptyMap()))
    private var step by mutableIntStateOf(15)
    private val snackbar = SnackbarHostState()

    private fun card() = compose.setContent {
        MeadowTheme(GardenTheme.MEADOW, DarkMode.LIGHT) {
            AppBackground {
                CompositionLocalProvider(LocalSnackbar provides snackbar) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        WeekCard(limit, DayOfWeek.MONDAY, step, enabled = true, onChange = { limit = it }, onStep = { step = it }, onHelp = {})
                    }
                }
            }
        }
    }

    private fun dayName(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())
    private fun short(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun day(day: Int): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("the grid's ${dayName(day)}") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(dayName(day) + ".") } == true
        },
    )

    private fun SemanticsNodeInteraction.act(@androidx.annotation.StringRes label: Int) {
        val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.first { it.label == text(label) }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

    /** A tap at [hour] o'clock on [day], when the whole day shows. */
    private fun tap(day: Int, hour: Float) = day(day).performTouchInput { click(Offset(center.x, height * hour / 24f)) }

    private fun type(vararg keys: String) = keys.forEach { compose.onNode(hasText(it)).performClick() }

    @Test
    fun `the keypad says when an amount is out of range, and delete and clear take digits off`() {
        card()
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 30m").performClick()
        type("2", "5", "00")
        node(R.string.keypad_up_to_day).assertExists()
        node(R.string.action_ok).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.keypad_delete)).performClick()
        node(R.string.keypad_up_to_day).assertDoesNotExist()
        node(R.string.action_ok).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.keypad_delete)).performTouchInput { longClick() }
        node(R.string.keypad_at_least).assertExists()
        compose.onNode(hasText("45m")).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(45, limit.daily)
    }

    @Test
    fun `a stretch is duplicated, gets a cap, and the cap's amount changes and goes`() {
        card()
        tap(1, 14f)
        node(R.string.week_duplicate).performClick()
        compose.onNodeWithContentDescription(dayName(6)).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(listOf(TimeWindow(600, 1320)), limit.schedule!!.days[6])

        // The stretch stays picked: its bar adds a cap with its hours.
        node(R.string.week_add_cap).performClick()
        node(R.string.limit_editor_cap_title).assertExists()
        node(R.string.action_ok).performClick()
        compose.waitForIdle()
        assertEquals(listOf(TimeCap(listOf(1), 600, 1320, 30)), limit.caps)

        // A tap in the cap's hours picks the cap.
        tap(1, 14f)
        node(R.string.week_amount).performClick()
        type("4", "5")
        node(R.string.action_ok).performClick()
        assertEquals(45, limit.caps.single().minutes)
        node(R.string.week_duplicate).performClick()
        compose.onNodeWithContentDescription(dayName(2)).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(listOf(1, 2), limit.caps.single().days)
        node(R.string.week_remove).performClick()
        assertTrue(limit.caps.isEmpty())
    }

    @Test
    fun `Redraw puts what is drawn next in place of the stretch, and the cross keeps it`() {
        card()
        tap(1, 14f)
        node(R.string.week_redraw).performClick()
        node(R.string.week_redraw_bar).assertExists()
        compose.onNodeWithContentDescription(text(R.string.week_redraw_stop)).performClick()
        node(R.string.week_redraw_bar).assertDoesNotExist()
        assertEquals(listOf(TimeWindow(600, 1320)), limit.schedule!!.days[1])

        tap(1, 14f)
        node(R.string.week_redraw).performClick()
        // While redrawing, a plain drag draws.
        day(1).performTouchInput {
            down(Offset(center.x, height * 8f / 24f))
            moveTo(Offset(center.x, height * 10f / 24f))
            moveTo(Offset(center.x, height * 12f / 24f))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(TimeWindow(480, 720)), limit.schedule!!.days[1])
        node(R.string.week_redraw_bar).assertDoesNotExist()
    }

    @Test
    fun `a handle moves the end of a picked stretch`() {
        card()
        tap(1, 14f)
        // Past the touch slop, then on to 23:00.
        day(1).performTouchInput {
            down(Offset(center.x, height * 22f / 24f))
            moveTo(Offset(center.x, height * 22.5f / 24f))
            moveTo(Offset(center.x, height * 23.2f / 24f))
            moveTo(Offset(center.x, height * 23.1f / 24f))
            moveTo(Offset(center.x, height * 23f / 24f))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(TimeWindow(600, 1380)), limit.schedule!!.days[1])
    }

    @Test
    fun `the ruler picks the drag steps, and small steps zoom the hours in`() {
        card()
        compose.onNodeWithContentDescription(text(R.string.week_steps_now, "15m")).performClick()
        compose.onNode(hasText("5m")).performClick()
        assertEquals(5, step)
        compose.onNodeWithContentDescription(text(R.string.week_zoom_out)).performClick()
        compose.onNodeWithContentDescription(text(R.string.week_zoom_in)).performClick()
        day(1).act(R.string.week_zoom_in)
        day(1).act(R.string.week_zoom_out)
        assertAccessible()
    }

    @Test
    fun `a day rested all day gets its hours back with Hours`() {
        card()
        compose.onNode(hasText(short(1))).performTouchInput { click() }
        node(R.string.week_day_resting).performClick()
        assertEquals(emptyList<TimeWindow>(), limit.schedule!!.days[1])
        node(R.string.week_day_hours).performClick()
        assertEquals(listOf(TimeWindow(600, 1320)), limit.schedule!!.days[1])
        // The day's bar also adds hours and a cap, and sets its own limit.
        node(R.string.week_add_hours).performClick()
        node(R.string.fine_tune_title).assertExists()
        node(R.string.action_cancel).performClick()
        compose.onNodeWithContentDescription(text(R.string.week_let_go)).performClick()
        node(R.string.week_day_hours).assertDoesNotExist()
    }

    @Test
    fun `a day is duplicated with its hours, its caps and its own limit`() {
        limit = limit.copy(caps = listOf(TimeCap(listOf(1), 840, 1320, 30)), dayMinutes = mapOf(1 to 60))
        card()
        day(1).act(R.string.week_duplicate)
        compose.onNodeWithContentDescription(dayName(6)).performClick()
        node(R.string.action_ok).performClick()
        assertEquals(listOf(TimeWindow(600, 1320)), limit.schedule!!.days[6])
        assertEquals(listOf(1, 6), limit.caps.single().days)
        assertEquals(60, limit.dayMinutes[6])
    }

    @Test
    fun `a ninth stretch on a day says the most a day can have`() {
        limit = limit.copy(schedule = Schedule(mapOf(1 to (0 until 8).map { TimeWindow(it * 60, it * 60 + 30) })))
        card()
        day(1).act(R.string.week_add_hours)
        node(R.string.action_ok).performClick()
        compose.waitUntil(5_000) { snackbar.currentSnackbarData != null }
        assertEquals(text(R.string.week_too_many, 8), snackbar.currentSnackbarData!!.visuals.message)
        assertEquals(8, limit.schedule!!.days[1]!!.size)
    }

    @Test
    fun `Fine-tune saves only hours on some day, and Cancel changes nothing`() {
        card()
        day(1).act(R.string.week_add_cap)
        compose.onNodeWithContentDescription(dayName(1)).performClick()
        node(R.string.fine_tune_pick_day).assertExists()
        node(R.string.action_ok).assertIsNotEnabled()
        compose.onNodeWithContentDescription(dayName(1)).performClick()
        node(R.string.action_ok).assertIsEnabled()
        assertAccessible()
        node(R.string.action_cancel).performClick()
        assertTrue(limit.caps.isEmpty())
    }
}
