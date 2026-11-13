package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.legal.LegalDoc
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Starting a garden and signing in, against the Auth emulator and the real functions and rules. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AuthRepositoryTest : EmulatorTest() {
    private val auth by lazy { AuthRepository(backend, store) }

    /** The one link that [send] emailed to [email]. */
    private suspend fun linkFrom(email: String, send: suspend () -> Unit): String {
        val before = emailedLinks(email).toSet()
        send()
        return emailedLinks(email).single { it !in before }
    }

    private fun profile(uid: String) = onBackend { backend.user(uid).get().await() }

    @Test
    fun `starting without an email makes an anonymous account with a garden`() = onBackend {
        val uid = newGarden("Robin")
        assertTrue(backend.auth.currentUser!!.isAnonymous)
        val doc = profile(uid)
        assertEquals("Robin", doc.getString("displayName"))
        assertEquals("🌼", doc.getString("flower"))
        // The backend gave the garden its friend code, in the owner-only account.
        val account = onBackend { backend.account(uid).get().await() }
        assertFalse(account.getString("friendCode").isNullOrBlank())
    }

    @Test
    fun `a garden needs a name`() {
        val error = assertThrows(FriendlyException::class.java) {
            onBackend { auth.startWithoutEmail("  ", "🌼", LegalDoc.CURRENT_VERSION) }
        }
        assertEquals(R.string.error_name_required, error.messageRes)
        assertNull(backend.auth.currentUser)
    }

    @Test
    fun `an email added later keeps the same garden`() = onBackend {
        val uid = newGarden("Robin")
        val email = newEmail()
        auth.addEmail(email, "longenough")
        assertFalse(backend.auth.currentUser!!.isAnonymous)

        backend.auth.signOut()
        auth.signIn(email, "longenough")
        assertEquals(uid, backend.uid)
        assertEquals("Robin", profile(uid).getString("displayName"))
    }

    @Test
    fun `a short password isn't added`() {
        newGarden("Robin")
        val error = assertThrows(FriendlyException::class.java) { onBackend { auth.addEmail(newEmail(), "short") } }
        assertEquals(R.string.error_auth_weak_password, error.messageRes)
        assertTrue(backend.auth.currentUser!!.isAnonymous)
    }

    @Test
    fun `signing up and in with a password, and a wrong password refused`() = onBackend {
        val email = newEmail()
        auth.signUp(email, "longenough")
        val uid = backend.uid
        backend.auth.signOut()

        assertThrows(FirebaseAuthException::class.java) { onBackend { auth.signIn(email, "wrong-password") } }
        assertNull(backend.uid)
        auth.signIn(" $email ", "longenough")
        assertEquals(uid, backend.uid)
    }

    @Test
    fun `a sign-in link signs in, once, and is kept until then`() = onBackend {
        val email = newEmail()
        auth.signUp(email, "longenough")
        val uid = backend.uid
        backend.auth.signOut()

        val link = linkFrom(email) { auth.sendSignInLink(" $email ") }
        assertEquals(email, auth.linkEmail)
        assertFalse(auth.receiveLink("https://example.com/not-a-sign-in-link"))
        assertTrue(auth.receiveLink(link))
        assertEquals(link, auth.incomingLink.value)

        auth.finishSignInLink(link, email)
        assertEquals(uid, backend.uid)
        assertNull(auth.incomingLink.value)
        assertNull(auth.linkEmail)
        // The same link again, after a rotation say, is ignored rather than failing.
        auth.finishSignInLink(link, email)
        assertEquals(uid, backend.uid)
    }

    @Test
    fun `a sign-in link attaches its address to a garden started without one`() = onBackend {
        val uid = newGarden("Robin")
        val email = newEmail()
        auth.finishSignInLink(linkFrom(email) { auth.sendSignInLink(email) }, email)

        assertEquals(uid, backend.uid)
        assertFalse(backend.auth.currentUser!!.isAnonymous)
        assertFalse(auth.anonymous.value)
    }

    @Test
    fun `a link for an address that has a garden can't take over a start that has its own`() = onBackend {
        val email = newEmail()
        auth.signUp(email, "longenough")
        val owner = backend.uid
        backend.auth.signOut()

        // A new start with a garden of its own can't take the address: it would lose that garden.
        newGarden("Sam")
        val link = linkFrom(email) { auth.sendSignInLink(email) }
        assertThrows(FirebaseAuthUserCollisionException::class.java) { onBackend { auth.finishSignInLink(link, email) } }
        assertNotEquals(owner, backend.uid)
    }

    @Test
    fun `a link for an address that has a garden signs an unused start in to it`() = onBackend {
        val email = newEmail()
        auth.signUp(email, "longenough")
        val owner = backend.uid
        backend.auth.signOut()

        // Signed in without a garden yet: say, its profile wasn't made because the phone was offline.
        backend.auth.signInAnonymously().await()
        auth.finishSignInLink(linkFrom(email) { auth.sendSignInLink(email) }, email)
        assertEquals(owner, backend.uid)
        assertFalse(backend.auth.currentUser!!.isAnonymous)
    }

    @Test
    fun `a link for a new address signs an unused start in with it`() = onBackend {
        backend.auth.signInAnonymously().await()
        val start = backend.uid
        val email = newEmail()
        auth.finishSignInLink(linkFrom(email) { auth.sendSignInLink(email) }, email)

        // The start had nothing to keep; the address is now its own account.
        assertNotEquals(start, backend.uid)
        assertEquals(email, backend.auth.currentUser!!.email)
        assertFalse(backend.auth.currentUser!!.isAnonymous)
    }

    @Test
    fun `a password reset needs an address`() {
        val error = assertThrows(FriendlyException::class.java) { onBackend { auth.sendPasswordReset(" ") } }
        assertEquals(R.string.error_email_required, error.messageRes)
        onBackend {
            val email = newEmail()
            auth.signUp(email, "longenough")
            auth.sendPasswordReset(email)
        }
    }
}
