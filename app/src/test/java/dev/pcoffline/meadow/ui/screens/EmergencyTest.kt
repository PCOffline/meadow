package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.emergency.EmergencyDialog
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The emergency unlock: slow and deliberate to start, and easy to end. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EmergencyTest : ScreenTest() {
    @Before
    fun freshStore() = AppGraph.store.clearAll()

    private fun unlockButton(seconds: Int) = node(R.string.emergency_dialog_unlock_countdown, seconds)

    @Test
    fun `signed out, unlocking waits for a reason and five seconds`() {
        compose.mainClock.autoAdvance = false
        show { EmergencyDialog("com.example.app", "Pebbles", onDismiss = {}) }
        compose.mainClock.advanceTimeByFrame()

        node(R.string.emergency_dialog_signed_out).assertExists()
        unlockButton(5).assertIsNotEnabled()
        compose.onNodeWithText("10 more characters", substring = true).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("My bus is late")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("more character", substring = true).assertDoesNotExist()
        // Still counting down: a reason alone isn't enough.
        unlockButton(5).assertIsNotEnabled()

        compose.mainClock.advanceTimeBy(2_000)
        unlockButton(3).assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(3_000)
        node(R.string.emergency_dialog_unlock).assertIsEnabled()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a short reason says how much more it needs, and keeps unlocking off`() {
        show { EmergencyDialog(null, null, onDismiss = {}) }
        compose.onNode(hasSetTextAction()).performTextInput("Help")
        compose.onNodeWithText("6 more characters", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(5_000)
        node(R.string.emergency_dialog_unlock).assertIsNotEnabled()
    }

    @Test
    fun `opened for an app, it unlocks just that app or all of them`() {
        show { EmergencyDialog("com.example.app", "Pebbles", onDismiss = {}) }
        node(R.string.emergency_dialog_just_app, "Pebbles").assertIsSelected()
        node(R.string.emergency_dialog_all_apps).performClick().assertIsSelected()
        node(R.string.emergency_dialog_just_app, "Pebbles").assertIsNotSelected()
    }

    @Test
    fun `opened from the garden, there is no choice of apps`() {
        show { EmergencyDialog(null, null, onDismiss = {}) }
        node(R.string.emergency_dialog_all_apps).assertDoesNotExist()
    }

    @Test
    fun `cancel closes the dialog`() {
        var dismissed = 0
        show { EmergencyDialog(null, null, onDismiss = { dismissed++ }) }
        node(R.string.action_cancel).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `while an unlock is on, the banner says for how long, and ends it`() {
        AppGraph.store.setEmergency(Emergency(System.currentTimeMillis() + 30 * 60_000L, null))
        show { EmergencyBanner() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("All limits paused for", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertAccessible(requireHeading = false)

        node(R.string.emergency_banner_end).performClick()
        compose.waitUntil(5_000) { AppGraph.store.emergency.value?.isActive() == false }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.emergency_title))).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `an unlock for one app names it`() {
        AppGraph.store.setEmergency(Emergency(System.currentTimeMillis() + 15 * 60_000L, "com.example.app"))
        show { EmergencyBanner() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("paused for", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("All limits paused", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with no unlock on, there is no banner`() {
        show { EmergencyBanner() }
        node(R.string.emergency_title).assertDoesNotExist()
    }
}
