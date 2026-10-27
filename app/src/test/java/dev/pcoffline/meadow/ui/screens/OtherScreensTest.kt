package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.WeekRule
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.enforcement.ProtectionState
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
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
    fun `a first run starts weeks the region's way, and can pick another day`() {
        show { AuthScreen() }
        // Robolectric runs as en-US, where weeks start on Sunday.
        compose.waitUntil(5_000) { AppGraph.store.weekStartSet }
        assertEquals(WeekRule(DayOfWeek.SUNDAY), AppGraph.store.weekRule.value)
        repeat(2) { node(R.string.intro_next).performClick() }
        node(R.string.intro_begin).performClick()
        node(R.string.settings_week_start).performScrollTo()
        compose.onNode(hasText(DayOfWeek.SUNDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())) and isSelectable()).assertIsSelected()
        compose.onNodeWithText(DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())).performScrollTo().performClick()
        // Nothing to keep yet, so at once.
        assertEquals(WeekRule(DayOfWeek.MONDAY), AppGraph.store.weekRule.value)
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
        // Which step comes first depends on what this device already allows.
        val explained = listOf(R.string.disclosure_usage, R.string.disclosure_keeper, R.string.disclosure_listener)
            .sumOf { compose.onAllNodesWithText(text(it)).fetchSemanticsNodes().size }
        assertEquals(1, explained)
        node(R.string.disclosure_not_now).performClick()
        node(R.string.disclosure_title).assertDoesNotExist()
    }

    @Test
    fun `a greyed-out switch is explained where it happens, with a way to App info`() {
        show { SetupScreen(onDone = {}) }
        // The garden keeper and notification access are the two Android holds back.
        assertEquals(2, compose.onAllNodesWithText(text(R.string.setup_restricted_action)).fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithText(text(R.string.setup_restricted_hint)).fetchSemanticsNodes().size)
        assertAccessible()
    }

    @Test
    fun `setup has one way out, into the garden only once the essentials are on`() {
        var state by mutableStateOf(ProtectionState(false, false, false, false, false, false, notificationListener = false))
        var left = 0
        // A new state starts the screen afresh, as coming back from Android's settings would.
        show { key(state) { SetupScreen(onDone = { left++ }, read = { state }) } }
        assertEquals(3, compose.onAllNodesWithText(text(R.string.setup_step_required)).fetchSemanticsNodes().size)
        node(R.string.setup_done).assertDoesNotExist()
        node(R.string.setup_later).performScrollTo().performClick()

        state = ProtectionState(true, true, false, false, false, false, notificationListener = true)
        node(R.string.setup_step_required).assertDoesNotExist()
        node(R.string.setup_later).assertDoesNotExist()
        node(R.string.setup_done).performScrollTo().performClick()
        assertEquals(2, left)
        assertAccessible()
    }

    @Test
    fun `settings show privacy choices, legal documents and a careful delete`() {
        val opened = mutableListOf<LegalDoc>()
        val shy = DemoGarden.robin.copy(privacy = Privacy(shareTime = false))
        show { SettingsScreen(shy, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = { opened += it }) }
        compose.onNode(isToggleable() and hasText(text(R.string.settings_share_time))).performScrollTo().assertIsOff()
        node(R.string.legal_privacy).performScrollTo().performClick()
        node(R.string.legal_terms).performScrollTo().performClick()
        assertEquals(listOf(LegalDoc.PRIVACY, LegalDoc.TERMS), opened)

        node(R.string.settings_delete).performScrollTo().assertExists()
        // The delete dialog (it needs DELETE typed first) is checked on a device in GardenJourneyTest:
        // its text field never lets Robolectric's Compose go idle.
        assertAccessible()
    }

    @Test
    fun `the profile card says Edit your profile only once`() {
        show { SettingsScreen(DemoGarden.robin.copy(bio = ""), onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }
        // Shown as the card's text without a bio, so the arrow stays quiet.
        node(R.string.profile_open).assertExists()
        compose.onAllNodesWithContentDescription(text(R.string.profile_open)).assertCountEquals(0)
    }

    @Test
    fun `with a bio, the profile card's arrow says where it leads`() {
        show { SettingsScreen(DemoGarden.robin.copy(bio = "Gardening, slowly"), onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }
        compose.onAllNodesWithContentDescription(text(R.string.profile_open)).assertCountEquals(1)
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
