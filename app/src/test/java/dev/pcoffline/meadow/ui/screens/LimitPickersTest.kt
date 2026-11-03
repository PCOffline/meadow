package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.CapTemplates
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.limits.MinutesStepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The limit's length picker (#76), the tappable length (#77), new caps (#78) and Share (#80). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class LimitPickersTest : ScreenTest() {
    private val pkg = "com.instagram.android"

    private fun editor(state: LimitEditorState) = show {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(pkg, state.copy(appName = "Instagram", loaded = true), onDone = {})
        }
    }

    private val pickable = SemanticsMatcher("opens the length picker") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == text(R.string.limit_editor_pick_time)
    }

    private fun hour(h: Int) = compose.onNode(hasText(text(R.string.duration_hours, h)) and isSelectable())
    private fun minute(m: Int) = compose.onNode(hasText(text(R.string.duration_minutes, m)) and isSelectable())

    private fun openPicker(value: Int = 30, onChange: (Int) -> Unit = {}) {
        show { MinutesStepper(value, step = 5, min = 5, max = 16 * 60, enabled = true, label = "Daily limit", onChange = onChange) }
        compose.onNode(pickable).performClick()
    }

    @Test
    fun `the picker offers every hour up to 16 and no more`() {
        openPicker()
        (0..16).forEach { hour(it).performScrollTo().assertExists() }
        hour(17).assertDoesNotExist()
        // No clock face is left.
        compose.onNodeWithText("23").assertDoesNotExist()
    }

    @Test
    fun `minutes come in steps of five`() {
        openPicker()
        (0..55 step 5).forEach { minute(it).performScrollTo().assertExists() }
        minute(3).assertDoesNotExist()
        minute(60).assertDoesNotExist()
    }

    @Test
    fun `it opens on the current length`() {
        openPicker(value = 95)
        hour(1).assertIsSelected()
        minute(35).assertIsSelected()
        hour(0).assertIsNotSelected()
    }

    @Test
    fun `at 16 hours only whole hours can be picked, and the minutes go back to 0`() {
        openPicker(value = 30)
        hour(16).performScrollTo().performClick()
        minute(0).assertIsSelected()
        minute(5).assertIsNotEnabled()
        minute(55).assertIsNotEnabled()
    }

    @Test
    fun `under the shortest length, the minutes that would make it shorter are off`() {
        openPicker(value = 30)
        hour(0).performScrollTo().performClick()
        minute(0).assertIsNotEnabled()
        minute(5).assertIsEnabled()
    }

    @Test
    fun `OK gives exactly the length shown`() {
        var picked = -1
        openPicker(value = 30) { picked = it }
        hour(2).performScrollTo().performClick()
        minute(25).performScrollTo().performClick()
        compose.onNodeWithText("2h 25m").assertExists()
        node(R.string.action_ok).performClick()
        assertEquals(2 * 60 + 25, picked)
    }

    @Test
    fun `the tappable length is a rounded button, not a bare rectangle`() {
        show { MinutesStepper(30, step = 5, min = 5, max = 16 * 60, enabled = true, label = "Daily limit") {} }
        val image = compose.onNode(pickable).captureToImage().asAndroidBitmap()
        val corner = image.getPixel(0, 0)
        val edge = image.getPixel(1, image.height / 2)
        // The corner is cut off and shows the page; the side shows the button's own fill.
        assertNotEquals("the side has its own fill", corner, edge)
    }

    @Test
    fun `a new cap starts with no days and no hours`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 60, weeklyMinutes = 0)))
        node(R.string.limit_editor_cap_add).performScrollTo().performClick()
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            .forEach { compose.onNodeWithContentDescription(it).assertIsNotSelected() }
        node(R.string.cap_start).assertExists()
        node(R.string.cap_end).assertExists()
        node(R.string.action_ok).assertIsNotEnabled()
    }

    @Test
    fun `templates fill in the days and the hours, and together they make a cap`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 60, weeklyMinutes = 0)))
        node(R.string.limit_editor_cap_add).performScrollTo().performClick()
        // The week starts where the phone, or the person, says; other tests may have changed it.
        val first = dev.pcoffline.meadow.AppGraph.store.weekRule.value.first
        fun day(n: Int) = compose.onNodeWithContentDescription(java.time.DayOfWeek.of(n).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
        node(R.string.cap_template_weekdays).performClick()
        CapTemplates.weekdays(first).forEach { day(it).assertIsSelected() }
        CapTemplates.weekends(first).forEach { day(it).assertIsNotSelected() }
        node(R.string.action_ok).assertIsNotEnabled()
        node(R.string.cap_template_evenings).performClick()
        compose.onNodeWithText(dev.pcoffline.meadow.ui.clockText(17 * 60)).assertExists()
        compose.onNodeWithText(dev.pcoffline.meadow.ui.clockText(22 * 60)).assertExists()
        node(R.string.action_ok).assertIsEnabled()
        node(R.string.cap_template_weekends).performClick()
        CapTemplates.weekends(first).forEach { day(it).assertIsSelected() }
        CapTemplates.weekdays(first).forEach { day(it).assertIsNotSelected() }
    }

    @Test
    fun `Share stays in view, greyed out, until the changes are saved`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)))
        node(R.string.limit_editor_share).performScrollTo().assertIsEnabled()
        compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription, listOf(text(R.string.limit_editor_more))))
            .onFirst().performClick()
        node(R.string.limit_editor_share).performScrollTo().assertIsNotEnabled()
    }
}
