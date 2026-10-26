package dev.pcoffline.meadow

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.enforcement.MeadowNotificationListener
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end: strict mode can only be turned on with what it needs (unrestricted battery and
 * notification access), covers Meadow when one of them goes until it's fixed or turned off, and
 * turns off from Settings after asking. Each step is checked on screen and in the backend.
 */
@RunWith(AndroidJUnit4::class)
class StrictModeJourneyTest : EndToEndTest() {
    private val listener get() = "${app.packageName}/${MeadowNotificationListener::class.java.name}"

    @After
    fun takeBackProtections() {
        // Phone-wide settings outlast the app's data, so they're undone for the tests after.
        shell("cmd notification disallow_listener $listener")
        shell("dumpsys deviceidle whitelist -${app.packageName}")
    }

    private fun strictSwitch() = compose.onNode(isToggleable() and hasText(text(R.string.settings_strict_title)))

    private fun giveProtections() {
        shell("cmd notification allow_listener $listener")
        shell("dumpsys deviceidle whitelist +${app.packageName}")
        eventually("strict mode's protections on") { AppGraph.protection.current().strictReady }
    }

    @Test
    fun strictModeNeedsItsProtectionsAndCoversMeadowWithoutThem() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startAsGuest("Ari")
            val account = "users/${FirebaseAuth.getInstance().currentUser!!.uid}/private/account"
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()

            // Without its protections it isn't turned on; Settings says what's missing.
            strictSwitch().performScrollTo().performClick()
            waitFor(R.string.strict_needs_title)
            inDialog(text(R.string.action_cancel)).performClick()
            strictSwitch().assertIsOff()

            // With them, it is.
            giveProtections()
            strictSwitch().performScrollTo().performClick()
            waitFor(R.string.settings_strict_on)
            waitForBackend(account) { it.bool("strictMode") == true }

            // Notification access taken away: next time Meadow is opened, it's covered until fixed.
            shell("cmd notification disallow_listener $listener")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor(R.string.strict_gate_title)
            node(R.string.strict_gate_listener).assertExists()
            node(R.string.strict_gate_turn_off).performScrollTo().performClick()
            waitForBackend(account) { it.bool("strictMode") == false }
            eventually("the cover gone") { !showing(text(R.string.strict_gate_title)) }

            // On again, then off from Settings, which asks first. The switch sits low in Settings,
            // where the first "on" snackbar may still be over it.
            giveProtections()
            waitForSnackbarToGo(R.string.settings_strict_on)
            strictSwitch().performScrollTo().performClick()
            waitForBackend(account) { it.bool("strictMode") == true }
            waitForSnackbarToGo(R.string.settings_strict_on)
            eventually("the switch on, uncovered") { !showing(text(R.string.strict_gate_title)) && runCatching { strictSwitch().assertIsOn() }.isSuccess }
            strictSwitch().performScrollTo().performClick()
            waitFor(R.string.settings_strict_off_title)
            inDialog(text(R.string.settings_strict_off_button)).performClick()
            waitFor(R.string.settings_strict_off_done)
            waitForBackend(account) { it.bool("strictMode") == false }
        }
    }
}
