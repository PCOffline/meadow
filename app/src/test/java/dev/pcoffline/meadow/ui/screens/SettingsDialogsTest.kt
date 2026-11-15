package dev.pcoffline.meadow.ui.screens

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The settings screen's questions before strict mode or signing out. They ask; nothing is sent until the person agrees. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class SettingsDialogsTest : ScreenTest() {
    private fun strictSwitch() = compose.onNode(isToggleable() and hasText(text(R.string.settings_strict_title)))

    @Test
    fun `strict mode on a phone that isn't set up asks to set it up first`() {
        var setups = 0
        show { SettingsScreen(DemoGarden.robin.copy(strictMode = false), onBack = {}, onOpenSetup = { setups++ }, onOpenProfile = {}, onOpenLegal = {}) }

        strictSwitch().performScrollTo().assertIsOff().performClick()
        node(R.string.strict_needs_title).assertExists()
        assertAccessible(requireHeading = false)

        node(R.string.action_cancel).performClick()
        node(R.string.strict_needs_title).assertDoesNotExist()
        assertEquals(0, setups)

        strictSwitch().performScrollTo().performClick()
        // The screen has a button with the same words; this is the dialog's.
        compose.onNode(hasText(text(R.string.settings_protections_button)) and hasAnyAncestor(isDialog())).performClick()
        node(R.string.strict_needs_title).assertDoesNotExist()
        assertEquals(1, setups)
    }

    @Test
    fun `turning strict mode off asks first, and keeping it changes nothing`() {
        show { SettingsScreen(DemoGarden.robin.copy(strictMode = true), onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }

        strictSwitch().performScrollTo().assertIsOn().performClick()
        node(R.string.settings_strict_off_title).assertExists()
        node(R.string.settings_strict_off_button).assertExists()

        node(R.string.settings_strict_keep).performClick()
        node(R.string.settings_strict_off_title).assertDoesNotExist()
        strictSwitch().performScrollTo().assertIsOn()
    }

    @Test
    fun `signing out asks first, and staying keeps the person signed in`() {
        show { SettingsScreen(DemoGarden.robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }

        node(R.string.settings_sign_out).performScrollTo().performClick()
        node(R.string.settings_sign_out_title).assertExists()
        node(R.string.settings_sign_out_body).assertExists()

        node(R.string.settings_sign_out_stay).performClick()
        node(R.string.settings_sign_out_title).assertDoesNotExist()
    }
}
