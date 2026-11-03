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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.TimeCap
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.ui.limits.DurationChoices
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
    fun `picking the minutes gives the length at once, so any length is three taps`() {
        var picked = -1
        // Tap one: the length.
        openPicker(value = 30) { picked = it }
        // Tap two: its hours. Tap three: its minutes.
        hour(2).performScrollTo().performClick()
        minute(25).performScrollTo().performClick()
        assertEquals(2 * 60 + 25, picked)
    }

    @Test
    fun `OK gives the length shown, for a change of hours alone`() {
        var picked = -1
        openPicker(value = 30) { picked = it }
        hour(3).performScrollTo().performClick()
        compose.onNodeWithText("3h 30m").assertExists()
        node(R.string.action_ok).performClick()
        assertEquals(3 * 60 + 30, picked)
    }

    @Test
    fun `every length from 5 minutes to 16 hours, in steps of 5, can be picked`() {
        val reachable = DurationChoices.hours(5, 16 * 60).flatMap { h -> DurationChoices.MINUTES.map { h * 60 + it } }.filter { it in 5..16 * 60 }
        assertEquals((5..16 * 60 step 5).toList(), reachable.sorted())
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
        // The weekend is the region's own.
        val region = CapTemplates.region()
        fun day(n: Int) = compose.onNodeWithContentDescription(java.time.DayOfWeek.of(n).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
        node(R.string.cap_template_weekdays).performClick()
        CapTemplates.weekdays(region).forEach { day(it).assertIsSelected() }
        CapTemplates.weekends(region).forEach { day(it).assertIsNotSelected() }
        node(R.string.action_ok).assertIsNotEnabled()
        node(R.string.cap_template_evenings).performClick()
        compose.onNodeWithText(dev.pcoffline.meadow.ui.clockText(17 * 60)).assertExists()
        compose.onNodeWithText(dev.pcoffline.meadow.ui.clockText(22 * 60)).assertExists()
        node(R.string.action_ok).assertIsEnabled()
        node(R.string.cap_template_weekends).performClick()
        CapTemplates.weekends(region).forEach { day(it).assertIsSelected() }
        CapTemplates.weekdays(region).forEach { day(it).assertIsNotSelected() }
    }

    @Test
    fun `each region has its own weekend`() {
        assertEquals(listOf(6, 7), CapTemplates.weekends("US"))
        assertEquals(listOf(1, 2, 3, 4, 5), CapTemplates.weekdays("US"))
        assertEquals(listOf(5, 6), CapTemplates.weekends("IL"))
        assertEquals(listOf(1, 2, 3, 4, 7), CapTemplates.weekdays("IL"))
        assertEquals(listOf(6, 7), CapTemplates.weekends("DE"))
    }

    @Test
    fun `a saved change goes back, and its note offers to share the limit as saved`() {
        var done = 0
        compose.setContent {
            dev.pcoffline.meadow.ui.theme.MeadowTheme {
                dev.pcoffline.meadow.ui.SnackbarScope {
                    LimitEditor(
                        pkg, LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), appName = "Instagram", loaded = true),
                        onDone = { done++ }, save = { _, _, _ -> ChangeOutcome.APPLIED },
                    )
                }
            }
        }
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less))[0].performClick()
        node(R.string.action_save).performScrollTo().performClick()
        compose.waitUntil(5_000) { done == 1 }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.share_title))).fetchSemanticsNodes().isNotEmpty() }
        node(R.string.limit_editor_updated).assertExists()
        node(R.string.share_title).performClick()
        val shared = dev.pcoffline.meadow.ui.components.ShareCommitment.shared.value
        assertTrue(shared?.text.orEmpty().contains("Instagram"))
        assertEquals(pkg, shared?.limit?.packageName)
        dev.pcoffline.meadow.ui.components.ShareCommitment.close()
    }

    private fun editorSaving(state: LimitEditorState, outcome: ChangeOutcome, onDone: () -> Unit) = show {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(pkg, state.copy(appName = "Instagram", loaded = true), onDone = onDone, save = { _, _, _ -> outcome })
        }
    }

    @Test
    fun `a new limit goes back to the garden once planted`() {
        var done = 0
        editorSaving(LimitEditorState(), ChangeOutcome.APPLIED) { done++ }
        node(R.string.limit_editor_plant).performScrollTo().performClick()
        compose.waitUntil(5_000) { done == 1 }
    }

    @Test
    fun `a question for a Bumblebee closes the editor`() {
        // Asking needs a connection.
        Connectivity.set(true)
        var done = 0
        editorSaving(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0), bumblebeeCount = 1, pendingKnown = true), ChangeOutcome.REQUESTED) { done++ }
        compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more))[0].performClick()
        node(R.string.limit_editor_ask).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { done == 1 }
    }

    @Test
    fun `the length picker is readable and reachable for everyone`() {
        openPicker(value = 30)
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `the cap dialog is readable and reachable for everyone, empty and filled in`() {
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", dailyMinutes = 60, weeklyMinutes = 0)))
        node(R.string.limit_editor_cap_add).performScrollTo().performClick()
        // With no times yet, each time button says what it is for.
        compose.onNodeWithContentDescription(text(R.string.cap_pick_start)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.cap_pick_end)).assertExists()
        assertAccessible(requireHeading = false)
        node(R.string.cap_template_weekdays).performClick()
        node(R.string.cap_template_evenings).performClick()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a saved cap opens as it is, with its quick picks lit, and saves unchanged`() {
        val cap = TimeCap(CapTemplates.weekdays(CapTemplates.region()), 17 * 60, 22 * 60, 45)
        editor(LimitEditorState(existing = AppLimit(pkg, "Instagram", 60, 0, caps = listOf(cap))))
        compose.onNode(SemanticsMatcher("edits a cap") { it.config.getOrNull(SemanticsActions.OnClick)?.label == text(R.string.limit_editor_cap_edit) }).performScrollTo().performClick()
        node(R.string.cap_template_weekdays).assertIsSelected()
        node(R.string.cap_template_evenings).assertIsSelected()
        node(R.string.cap_template_mornings).assertIsNotSelected()
        compose.onNodeWithText(dev.pcoffline.meadow.ui.clockText(17 * 60)).assertExists()
        // In the cap's row behind the dialog, and in the dialog's own length.
        compose.onAllNodesWithText("45m").assertCountEquals(2)
        node(R.string.action_ok).assertIsEnabled().performClick()
        // Unchanged, so there is nothing to save.
        node(R.string.action_save).performScrollTo().assertIsNotEnabled()
    }

}
