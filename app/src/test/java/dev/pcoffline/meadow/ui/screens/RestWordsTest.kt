package dev.pcoffline.meadow.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.enforcement.RestState
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.minutesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the rest screen says, by why the app rests or why it's open again. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RestWordsTest : ScreenTest() {
    private val limit = AppLimit("demo.tiktok", "TikTok", 30, 0)
    private val minute = 60_000L

    private fun says(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `each reason reads as itself`() {
        var state by mutableStateOf(DemoGarden.rest.copy(budget = null))
        show { RestContent("demo.tiktok", state) }
        // Before its budget is read, the words are general.
        assertTrue(says(text(R.string.rest_body_generic)))

        // A week's limit used up.
        state = DemoGarden.rest.copy(budget = AppBudget(limit.copy(dailyMinutes = 0, weeklyMinutes = 120), 10 * minute, 120 * minute, 0, 0))
        assertTrue(says(text(R.string.rest_body_week, app.resources.minutesText(120))))

        // Outside its hours, with no hours to come this week.
        state = DemoGarden.rest.copy(budget = AppBudget(limit, 0, 0, 0, 0, outsideHours = true, untilScheduleChangeMs = null))
        assertTrue(says(text(R.string.rest_body_hours_closed, "TikTok")))

        // A Bumblebee said yes: open again, with time left.
        state = DemoGarden.rest.copy(budget = AppBudget(limit, 20 * minute, 20 * minute, 15, 15))
        assertTrue(says(text(R.string.rest_title_yes)))

        // An emergency unlock: open, and for how long.
        state = DemoGarden.rest.copy(budget = AppBudget(limit, 40 * minute, 40 * minute, 0, 0, emergencyUntilMillis = System.currentTimeMillis() + 15 * minute))
        assertTrue(says(text(R.string.rest_title_emergency)))
        assertAccessible()
    }

    @Test
    fun `signed out, the screen still rests the app, without asking anyone`() {
        show { RestContent("demo.tiktok", RestState(appName = "TikTok", budget = AppBudget(limit, 30 * minute, 30 * minute, 0, 0))) }
        assertTrue(says(text(R.string.rest_title, "TikTok")))
    }

    @Test
    fun `resting with Bumblebees, more time can be asked for, and Home leaves`() {
        var homes = 0
        val resting = RestState(
            appName = "TikTok", budget = AppBudget(limit, 30 * minute, 30 * minute, 0, 0), signedIn = true, hasBumblebees = true,
        )
        show { RestContent("demo.tiktok", resting, onHome = { homes++ }) }
        node(R.string.rest_home).performClick()
        assertEquals(1, homes)

        node(R.string.rest_ask).assertExists()
        node(R.string.rest_emergency).assertExists()
    }

    @Test
    fun `a yes while the screen is open opens the app again, and Open goes to it`() {
        val opened = mutableListOf<String>()
        var homes = 0
        var state by mutableStateOf(RestState(appName = "TikTok", budget = AppBudget(limit, 30 * minute, 30 * minute, 0, 0), signedIn = true))
        show { RestContent("demo.tiktok", state, onHome = { homes++ }, onOpen = { opened += it }) }
        assertTrue(says(text(R.string.rest_no_bees)))

        // A Bumblebee's yes arrives: the app is open again for its extra time.
        state = state.copy(budget = AppBudget(limit, 30 * minute, 30 * minute, 15, 15))
        compose.waitForIdle()
        node(R.string.rest_open, "TikTok").performClick()
        assertEquals(listOf("demo.tiktok"), opened)
        node(R.string.rest_home).performClick()
        assertEquals(1, homes)

        // Resting again, then an emergency unlock opens it without petals.
        state = state.copy(budget = AppBudget(limit, 30 * minute, 30 * minute, 0, 0))
        compose.waitForIdle()
        state = state.copy(budget = AppBudget(limit, 30 * minute, 30 * minute, 0, 0, emergencyUntilMillis = System.currentTimeMillis() + 15 * minute))
        compose.waitForIdle()
        assertTrue(says(text(R.string.rest_title_emergency)))
    }
}
