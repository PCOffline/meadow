package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.limits.WeekGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The guide to the week card: its five steps, once by itself, and again from the ?. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WeekGuideTest : ScreenTest() {
    private val pkg = "com.instagram.android"

    @Test
    fun `the guide has five steps, and Got it on the last closes it`() {
        var done = 0
        show { WeekGuide("Instagram", DayOfWeek.MONDAY) { done++ } }
        node(R.string.guide_draw_title, "Instagram").assertExists()
        compose.onNodeWithContentDescription(text(R.string.guide_step, 1, 5)).assertExists()
        assertAccessible()
        listOf(R.string.guide_stretch_title, R.string.guide_day_title, R.string.guide_tools_title, R.string.guide_time_title).forEach {
            node(R.string.guide_next).performClick()
            node(it).assertExists()
        }
        compose.onNodeWithContentDescription(text(R.string.guide_step, 5, 5)).assertExists()
        node(R.string.action_back).performClick()
        node(R.string.guide_tools_title).assertExists()
        node(R.string.guide_next).performClick()
        node(R.string.guide_done).performClick()
        assertEquals(1, done)
    }

    @Test
    fun `Skip on the first step closes it`() {
        var done = 0
        show { WeekGuide("Instagram", DayOfWeek.MONDAY) { done++ } }
        node(R.string.guide_skip).performClick()
        assertEquals(1, done)
    }

    @Test
    fun `the editor shows the guide by itself once, and again from the question mark`() {
        show {
            Column {
                LimitEditor(pkg, LimitEditorState(existing = AppLimit(pkg, "Instagram", 30, 0), appName = "Instagram", loaded = true), onDone = {}, firstTimeGuide = true)
            }
        }
        node(R.string.guide_draw_title, "Instagram").assertExists()
        node(R.string.guide_skip).performClick()
        assertTrue(AppGraph.store.weekGuideSeen.value)
        node(R.string.guide_draw_title, "Instagram").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.week_help)).performClick()
        node(R.string.guide_draw_title, "Instagram").assertExists()
    }

    @Test
    fun `once seen, the guide doesn't show by itself`() {
        AppGraph.store.setWeekGuideSeen()
        show {
            LimitEditor(pkg, LimitEditorState(existing = AppLimit(pkg, "Instagram", 30, 0), appName = "Instagram", loaded = true), onDone = {}, firstTimeGuide = true)
        }
        node(R.string.guide_draw_title, "Instagram").assertDoesNotExist()
    }
}
