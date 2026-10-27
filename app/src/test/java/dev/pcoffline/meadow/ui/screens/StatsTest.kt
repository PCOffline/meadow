package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.PeriodStats
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.stats.StatsUiState
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.colorSchemeFor
import dev.pcoffline.meadow.ui.theme.gardenColorsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

/** Growth's numbers: honest about days it has no record of, and coloured by whether they're good news. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StatsTest : ScreenTest() {
    private val week = DemoGarden.stats
    private val min = 60_000L

    /** The colour just inside the pill holding [text], beside the words. */
    private fun pillColour(text: String): Color {
        val bounds = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        return shot[(bounds.left - 4).toInt(), bounds.center.y.toInt()]
    }

    private fun assertClose(expected: Color, actual: Color) = assertTrue(
        "expected $expected, was $actual",
        abs(expected.red - actual.red) < 0.02f && abs(expected.green - actual.green) < 0.02f && abs(expected.blue - actual.blue) < 0.02f,
    )

    @Test
    fun `less screen time than before is green`() {
        show { StatsContent(week) }
        assertClose(gardenColorsFor(GardenTheme.MEADOW, dark = false).better, pillColour("less than"))
    }

    @Test
    fun `more screen time than before is red`() {
        show { StatsContent(week.copy(stats = week.stats!!.copy(previousTotalMs = 600 * min))) }
        assertClose(colorSchemeFor(GardenTheme.MEADOW, dark = false).errorContainer, pillColour("more than"))
    }

    @Test
    fun `a day with no record is said so, left out of the average, and nothing is compared`() {
        val s = week.stats!!
        // Monday has no record: the rest add up to 6 days of 60 minutes.
        val days = s.days.mapIndexed { i, (day, _) -> day to if (i == 0) null else 60 * min }
        show { StatsContent(week.copy(stats = s.copy(days = days, totalMs = 360 * min, previousTotalMs = null))) }
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.stats_missing_days, 1, 1)).assertExists()
        compose.onNodeWithText("1h").assertExists()
        assertTrue(compose.onAllNodes(hasText("than", substring = true)).fetchSemanticsNodes().isEmpty())
        assertEquals(60 * min, s.copy(days = days).dailyAverageMs(s.to))
    }

    @Test
    fun `the current period shows progress over the weeks, past ones don't`() {
        val now = week.selection.copy(offset = 0)
        var state by mutableStateOf(week.copy(selection = now, stats = week.stats!!.copy(selection = now), progress = DemoGarden.progress))
        show { StatsContent(state) }
        node(R.string.progress_title).assertExists()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.progress_calmer_weeks, 3, 3)).assertExists()
        node(R.string.progress_kept, 5, 7).assertExists()
        // The summary already compares the week with the last; the card doesn't say it again.
        assertEquals(1, compose.onAllNodesWithText("less than", substring = true).fetchSemanticsNodes().size)
        assertAccessible()

        state = week.copy(progress = DemoGarden.progress)
        compose.waitForIdle()
        node(R.string.progress_title).assertDoesNotExist()
    }

    @Test
    fun `progress with nothing to show yet stays out of the way`() {
        val now = week.selection.copy(offset = 0)
        show { StatsContent(week.copy(selection = now, stats = week.stats!!.copy(selection = now), progress = Progress(null, 0, 0, 0))) }
        node(R.string.progress_title).assertDoesNotExist()
    }

    @Test
    fun `a day of the month opens on its own, by tap or by a screen reader, but days still to come don't`() {
        val today = Days.today()
        val from = today.withDayOfMonth(1)
        val to = from.plusMonths(1).minusDays(1)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { it to if (it.isAfter(today)) 0L else 30 * min }.toList()
        val month = Selection(Period.MONTH, 0)
        val stats = PeriodStats(month, from, to, days, apps = emptyList(), totalMs = today.dayOfMonth * 30 * min, previousTotalMs = null)
        val opened = mutableListOf<LocalDate>()
        show { StatsContent(StatsUiState(month, stats), onOpenDay = { opened += it }) }

        val pickable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        assertEquals(today.dayOfMonth, pickable.fetchSemanticsNodes().size)
        pickable[0].performTouchInput { click() }
        val last = pickable[today.dayOfMonth - 1].fetchSemanticsNode()
        assertTrue(last.config[SemanticsProperties.ContentDescription].single().endsWith(", 30m"))
        compose.runOnUiThread { last.config[SemanticsActions.CustomActions].single().action() }
        compose.waitForIdle()
        assertEquals(listOf(from, today), opened)
        assertAccessible()
    }

    @Test
    fun `a day picked from the month is shown as that day, never one still to come`() {
        val today = Days.today()
        assertEquals(Selection(Period.DAY, -3), Selection.ofDay(today.minusDays(3), today))
        assertEquals(Selection(Period.DAY, 0), Selection.ofDay(today, today))
        assertEquals(Selection(Period.DAY, 0), Selection.ofDay(today.plusDays(2), today))
    }
}
