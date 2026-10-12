package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.legal.LegalDoc
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.setup.SetupScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class OtherScreensTest : ScreenTest() {

    @Test
    fun `onboarding walks through the intro and needs a name and consent`() {
        show { AuthScreen() }
        assertAccessible()
        node(R.string.intro_next).performClick()
        node(R.string.intro_next).performClick()
        node(R.string.intro_begin).performClick()
        val plant = node(R.string.onboarding_plant)
        plant.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.onboarding_name_label))).performTextInput("Robin")
        plant.assertIsNotEnabled() // Still needs the terms accepted.
        node(R.string.legal_agree).performScrollTo().performClick()
        plant.assertIsEnabled()
        assertAccessible()
    }

    @Test
    fun `onboarding opens the terms before you agree to them`() {
        show { AuthScreen() }
        node(R.string.intro_have_garden).assertExists()
        repeat(2) { node(R.string.intro_next).performClick() }
        node(R.string.intro_begin).performClick()
        node(R.string.legal_terms).performScrollTo().performClick()
        // The link stays underneath; the dialog adds its title and the document's own heading.
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.legal_terms)).fetchSemanticsNodes().size >= 3 }
    }

    @Test
    fun `returning gardeners can use an emailed link or a password`() {
        show { AuthScreen() }
        node(R.string.intro_have_garden).performClick()
        val link = node(R.string.auth_email_link)
        link.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.field_email))).performTextInput("robin@example.com")
        link.assertIsEnabled()
        assertAccessible()

        node(R.string.auth_password_instead).performClick()
        val signIn = node(R.string.sign_in_button)
        signIn.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.field_password))).performTextInput("petals")
        signIn.assertIsEnabled()
        node(R.string.sign_in_forgot).assertExists()
        node(R.string.auth_link_instead).performClick()
        node(R.string.auth_email_link).assertExists()
        assertAccessible()
    }

    @Test
    fun `a garden without an email can be kept with a link or a password`() {
        show { dev.pcoffline.meadow.ui.auth.SecureAccountCard(R.string.settings_secure_reason) }
        node(R.string.secure_title).assertExists()
        node(R.string.auth_email_link).assertExists()
        node(R.string.auth_password_instead).performClick()
        node(R.string.secure_button).assertExists()
        assertTrue(compose.onAllNodesWithText(text(R.string.sign_in_forgot)).fetchSemanticsNodes().isEmpty())
        assertAccessible(requireHeading = true)
    }

    @Test
    fun `setup explains sensitive permissions before opening Android's settings`() {
        show { SetupScreen(onDone = {}) }
        assertAccessible()
        compose.onAllNodesWithText(text(R.string.setup_step_allow)).onFirst().performClick()
        node(R.string.disclosure_title).assertExists()
        node(R.string.disclosure_usage).assertExists()
        node(R.string.disclosure_not_now).performClick()
        node(R.string.disclosure_title).assertDoesNotExist()
    }

    @Test
    fun `settings show privacy choices, legal documents and a careful delete`() {
        val opened = mutableListOf<LegalDoc>()
        val shy = DemoGarden.robin.copy(privacy = Privacy(shareUsage = false, shareProtection = true))
        show { SettingsScreen(shy, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = { opened += it }) }
        compose.onNode(isToggleable() and hasText(text(R.string.settings_share_usage))).performScrollTo().assertIsOff()
        compose.onNode(isToggleable() and hasText(text(R.string.settings_share_protection))).assertIsOn()
        node(R.string.legal_privacy).performScrollTo().performClick()
        node(R.string.legal_terms).performScrollTo().performClick()
        assertEquals(listOf(LegalDoc.PRIVACY, LegalDoc.TERMS), opened)

        node(R.string.settings_delete).performScrollTo().performClick()
        val confirm = node(R.string.settings_delete_button)
        confirm.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.settings_delete_confirm_hint))).performTextInput("delete")
        confirm.assertIsEnabled()
        node(R.string.settings_delete_keep).performClick()
        confirm.assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `the profile only offers saving once something changed`() {
        show { ProfileScreen(DemoGarden.robin, onBack = {}) }
        val save = node(R.string.action_save)
        save.performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.profile_bio))).performScrollTo().performTextInput(" Also hiking.")
        save.performScrollTo().assertIsEnabled()
        assertAccessible()
    }

    @Test
    fun `colour choices are named, not just shown`() {
        show { ProfileScreen(DemoGarden.robin, onBack = {}) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(text(R.string.avatar_color_honey))).performScrollTo().assertExists()
    }
}
