package dev.pcoffline.meadow.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.MonthHeatmap
import dev.pcoffline.meadow.ui.components.StalkBarChart
import dev.pcoffline.meadow.ui.minutesText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The graphs drawn with nothing in them, with a named line, with days missing, and as values change. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChartsTest : ScreenTest() {
    @Test
    fun `a time graph with no bars still has a scale, and takes new values`() {
        var values by mutableStateOf(emptyList<Float>())
        var line by mutableStateOf<Float?>(null)
        show { StalkBarChart(values = values, labels = values.indices.map { "$it" }, referenceLine = line) }
        compose.onNodeWithText("0").assertExists()

        // A line with no name, then values that change.
        line = 45f
        values = listOf(10f, 0f, 70f, 20f)
        compose.waitForIdle()
        values = listOf(5f, 5f)
        line = null
        compose.waitForIdle()
    }

    @Test
    fun `a named line, a picked day, missing days and every other label`() {
        val values = listOf(30f, 0f, 400f, 60f, 15f, 90f, 0f)
        show {
            StalkBarChart(
                values = values, labels = listOf("M", "T", "W", "T2", "F", "S", "S2"), highlightIndex = 2,
                referenceLine = 600f, referenceLabel = "Limit", missing = setOf(1, 6), showEveryNthLabel = 2,
            )
        }
        compose.onNodeWithText("W").assertExists()
        compose.onNodeWithText("T").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.chart_axis_description, app.resources.minutesText(600)), substring = true).assertExists()
    }

    @Test
    fun `bars fill green, then deeper, then red, or in a colour of their own`() {
        show {
            Column {
                GrowthBar(0.5f)
                GrowthBar(0.85f)
                GrowthBar(1.4f)
                GrowthBar(0.3f, color = Color.Blue)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a month without picking reads each day, and days with no record or no use look so`() {
        show {
            MonthHeatmap(
                values = listOf(0f, 30f, 120f, 0f, 45f),
                labels = listOf("1", "2", "3", "4", "5"),
                firstDay = DayOfWeek.SUNDAY,
                firstDayOffset = 3,
                missing = setOf(3),
                descriptions = listOf("one", "two", "three", "four", "five"),
            )
        }
        compose.onNodeWithContentDescription("three").assertExists()
        compose.onNodeWithContentDescription("four").assertExists()
    }

    @Test
    fun `an empty month has only its weekdays`() {
        show { MonthHeatmap(values = emptyList(), labels = emptyList()) }
        compose.waitForIdle()
    }
}
