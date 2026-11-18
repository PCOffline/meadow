package dev.pcoffline.meadow.ui.screens

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.setup.StrictGate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** In strict mode, the app stays covered until Meadow is safe from battery savers and can reach notifications. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StrictGateTest : ScreenTest() {
    private fun started() = generateSequence { shadowOf(app).nextStartedActivity }.map { it.action }.toList()

    @Test
    fun `without strict mode, nothing covers the app`() {
        show { StrictGate(strictMode = false, onTroubleshoot = {}) }
        node(R.string.strict_gate_title).assertDoesNotExist()
    }

    @Test
    fun `each missing protection has a button to its setting, and help is a tap away`() {
        var help = 0
        show { StrictGate(strictMode = true, onTroubleshoot = { help++ }) }
        node(R.string.strict_gate_title).assertExists()
        assertAccessible()
        node(R.string.strict_gate_battery).performSemanticsAction(SemanticsActions.OnClick)
        node(R.string.strict_gate_listener).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(setOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), started().toSet())
        node(R.string.trouble_button).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, help)
    }

    @Test
    fun `a phone without the battery request opens the battery list instead`() {
        shadowOf(app).checkActivities(true)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.android.settings"; name = "Battery" }
            },
        )
        show { StrictGate(strictMode = true, onTroubleshoot = {}) }
        node(R.string.strict_gate_battery).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), started())
    }

    @Test
    fun `with battery savers kept away, only the missing one is asked for, and strict mode can be turned off`() {
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        show { StrictGate(strictMode = true, onTroubleshoot = {}) }
        node(R.string.strict_gate_battery).assertDoesNotExist()
        node(R.string.strict_gate_listener).assertExists()
        // These tests have no backend, so turning it off fails, and the gate says so.
        node(R.string.strict_gate_turn_off).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
