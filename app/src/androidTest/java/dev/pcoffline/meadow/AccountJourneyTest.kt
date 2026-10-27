package dev.pcoffline.meadow

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.ui.legal.LegalDoc
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, against the Auth emulator: the ways into and out of an account. A garden started
 * without an email keeps itself with a password or an emailed link; someone signs out and back
 * in (getting the password wrong first, and asking for a reset); an account with no profile yet
 * chooses its name; and terms accepted in an older version are asked for again.
 */
@RunWith(AndroidJUnit4::class)
class AccountJourneyTest : EndToEndTest() {
    private val people = mutableListOf<EmulatorFriend>()

    @After
    fun closePeople() {
        people.forEach { it.close() }
    }

    private val email = hasSetTextAction() and hasText(text(R.string.field_email))
    private val password = hasSetTextAction() and hasText(text(R.string.field_password))
    private fun address(name: String) = "$name-${System.currentTimeMillis()}@example.com"

    private fun JSONObject.termsVersion(): Int? =
        optJSONObject("acceptedTerms")?.getJSONObject("mapValue")?.getJSONObject("fields")?.int("version")

    @Test
    fun aGuestAddsAPasswordSignsOutAndSignsBackIn() {
        val address = address("ari")
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Ari")
            val uid = FirebaseAuth.getInstance().currentUser!!.uid

            // Settings offers to keep the garden; with a password it's the same garden, now with an email.
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            waitFor(R.string.secure_title)
            // Without an email, signing out would lose the garden, so it isn't offered.
            assertFalse(showing(text(R.string.settings_sign_out)))
            node(R.string.auth_password_instead).performScrollTo().performClick()
            typeInto(email, address)
            typeInto(password, EmulatorFriend.PASSWORD)
            node(R.string.secure_button).performScrollTo().performClick()
            eventually("the garden kept with an email") { !showing(text(R.string.secure_title)) }
            assertEquals(uid, FirebaseAuth.getInstance().currentUser!!.uid)
            assertEquals(address, FirebaseAuth.getInstance().currentUser!!.email)

            // Signing out goes back to the start.
            node(R.string.settings_sign_out).performScrollTo().performClick()
            inDialog(text(R.string.settings_sign_out)).performClick()
            waitFor(R.string.intro_have_garden)
            assertEquals(null, FirebaseAuth.getInstance().currentUser)

            // Back in with the password: a wrong one is refused kindly, and a reset can be asked for.
            node(R.string.intro_have_garden).performClick()
            waitFor(R.string.sign_in_title)
            node(R.string.auth_password_instead).performClick()
            typeInto(email, address)
            typeInto(password, "not-my-password")
            node(R.string.sign_in_button).performClick()
            waitFor(R.string.error_auth_wrong_credentials, substring = true)
            node(R.string.sign_in_forgot).performClick()
            waitFor(R.string.sign_in_reset_sent)
            waitForSnackbarToGo(R.string.sign_in_reset_sent)
            compose.onNode(password).performTextReplacement(EmulatorFriend.PASSWORD)
            node(R.string.sign_in_button).performClick()
            intoTheGarden("Ari")
            assertEquals(uid, FirebaseAuth.getInstance().currentUser!!.uid)
        }
    }

    @Test
    fun aGuestKeepsTheirGardenWithAnEmailedLink() {
        val address = address("noa")
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Noa")
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            waitFor(R.string.secure_title)
            typeInto(email, address)
            node(R.string.auth_email_link).performScrollTo().performClick()
            waitForText(text(R.string.secure_link_sent, address))

            // Opening the link attaches the email to this garden instead of starting another.
            openEmailedLink(address)
            waitFor(R.string.link_done)
            eventually("the garden kept with an email") { FirebaseAuth.getInstance().currentUser?.isAnonymous == false }
            assertEquals(uid, FirebaseAuth.getInstance().currentUser!!.uid)
            assertEquals(address, FirebaseAuth.getInstance().currentUser!!.email)
            eventually("the offer to keep the garden gone") { !showing(text(R.string.secure_title)) }
        }
    }

    @Test
    fun anAccountWithoutAProfileChoosesItsNameFirst() {
        val rae = EmulatorFriend.create(app, "Rae", "🌷", profile = false).also(people::add)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(R.string.intro_have_garden)
            node(R.string.intro_have_garden).performClick()
            node(R.string.auth_password_instead).performClick()
            typeInto(email, rae.email)
            typeInto(password, EmulatorFriend.PASSWORD)
            node(R.string.sign_in_button).performClick()

            // Signed in, but there's no garden yet: a name and the terms first.
            waitFor(R.string.profile_setup_title)
            typeInto(hasSetTextAction() and hasText(text(R.string.onboarding_name_label)), "Rae")
            node(R.string.legal_agree).performScrollTo().performClick()
            node(R.string.profile_setup_continue).performScrollTo().performClick()
            intoTheGarden("Rae")
            val profile = backendDoc("users/${rae.uid}")!!
            assertEquals("Rae", profile.string("displayName"))
            assertEquals(LegalDoc.CURRENT_VERSION, backendDoc("users/${rae.uid}/private/account")!!.termsVersion())
        }
    }

    @Test
    fun termsAcceptedInAnOlderVersionAreAskedForAgain() {
        val kai = EmulatorFriend.create(app, "Kai", "🌷", termsVersion = LegalDoc.CURRENT_VERSION - 1).also(people::add)
        signInAs(kai)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(R.string.legal_update_title)
            inDialog(text(R.string.legal_update_accept)).performClick()
            waitForBackend("users/${kai.uid}/private/account") { it.termsVersion() == LegalDoc.CURRENT_VERSION }
            eventually("the terms dialog gone") { !showing(text(R.string.legal_update_title)) }
            assertFalse(showing(text(R.string.legal_update_accept)))
        }
    }
}
