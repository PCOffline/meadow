package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import dev.pcoffline.meadow.R
import com.google.firebase.FirebaseNetworkException
import dev.pcoffline.meadow.ui.friendlyMessage
import dev.pcoffline.meadow.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Failed calls show the backend's own words, and never a raw code like "NOT_FOUND". */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BackendErrorsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun shown(message: String, code: Code?, unreachable: Boolean = false) =
        Backend.CallFailed(message, unreachable, code, null).friendlyMessage(context)

    @Test
    fun `the backend's own messages are kept`() {
        assertEquals("That request was already answered.", Backend.backendText(Code.FAILED_PRECONDITION, "That request was already answered."))
        assertEquals("That request is gone.", Backend.backendText(Code.NOT_FOUND, " That request is gone. "))
    }

    @Test
    fun `Firebase's own failures are not shown as they are`() {
        // A function that isn't deployed (or is in another region) comes back as just its code.
        assertNull(Backend.backendText(Code.NOT_FOUND, "NOT_FOUND"))
        assertNull(Backend.backendText(Code.PERMISSION_DENIED, "PERMISSION_DENIED"))
        // Crashes and bad responses: the backend never throws these on purpose.
        assertNull(Backend.backendText(Code.INTERNAL, "INTERNAL"))
        assertNull(Backend.backendText(Code.INTERNAL, "Response is not valid JSON object."))
        assertNull(Backend.backendText(Code.UNKNOWN, "Something odd"))
        assertNull(Backend.backendText(Code.NOT_FOUND, null))
        assertNull(Backend.backendText(Code.NOT_FOUND, "  "))
    }

    @Test
    fun `failures without the backend's words get a helpful message for their kind`() {
        // Faults on the server's side don't explain themselves: there's nothing to do but wait.
        assertEquals(context.getString(R.string.error_unavailable), shown("", Code.NOT_FOUND))
        assertEquals(context.getString(R.string.error_unavailable), shown("", Code.PERMISSION_DENIED))
        assertEquals(context.getString(R.string.error_auth_recent_login), shown("", Code.UNAUTHENTICATED))
        assertEquals(context.getString(R.string.error_server_busy), shown("", Code.RESOURCE_EXHAUSTED))
        assertEquals(context.getString(R.string.error_unavailable), shown("", Code.INTERNAL))
        assertEquals(context.getString(R.string.error_offline), shown("", Code.UNAVAILABLE, unreachable = true))
        assertEquals(context.getString(R.string.error_offline), shown("", null, unreachable = true))
        assertEquals("That request is gone.", shown("That request is gone.", Code.NOT_FOUND))
    }

    @Test
    fun `a sign-in method the project hasn't switched on says so`() {
        for (code in listOf("ERROR_OPERATION_NOT_ALLOWED", "ERROR_UNAUTHORIZED_DOMAIN", "ERROR_INVALID_CONTINUE_URI")) {
            assertEquals(context.getString(R.string.error_auth_not_enabled), FirebaseAuthException(code, "raw").friendlyMessage(context))
        }
    }

    @Test
    fun `a garden that can't be read says only what the person can act on`() {
        fun read(code: FirebaseFirestoreException.Code) =
            FirebaseFirestoreException(code.name, code).friendlyMessage(context)
        assertEquals(context.getString(R.string.error_unavailable), read(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(context.getString(R.string.error_unavailable), read(FirebaseFirestoreException.Code.NOT_FOUND))
        assertEquals(context.getString(R.string.error_unavailable), read(FirebaseFirestoreException.Code.INTERNAL))
        assertEquals(context.getString(R.string.error_offline), read(FirebaseFirestoreException.Code.UNAVAILABLE))
    }

    @Test
    fun `each sign-in failure says what to do about it`() {
        val codes = mapOf(
            "ERROR_INVALID_EMAIL" to R.string.error_auth_invalid_email,
            "ERROR_WRONG_PASSWORD" to R.string.error_auth_wrong_credentials,
            "ERROR_INVALID_CREDENTIAL" to R.string.error_auth_wrong_credentials,
            "ERROR_USER_NOT_FOUND" to R.string.error_auth_wrong_credentials,
            "ERROR_EMAIL_ALREADY_IN_USE" to R.string.error_auth_email_taken,
            "ERROR_CREDENTIAL_ALREADY_IN_USE" to R.string.error_auth_email_taken,
            "ERROR_WEAK_PASSWORD" to R.string.error_auth_weak_password,
            "ERROR_TOO_MANY_REQUESTS" to R.string.error_auth_too_many,
            "ERROR_REQUIRES_RECENT_LOGIN" to R.string.error_auth_recent_login,
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" to R.string.error_auth_other_method,
            "ERROR_INVALID_ACTION_CODE" to R.string.error_link_invalid,
            "ERROR_EXPIRED_ACTION_CODE" to R.string.error_link_invalid,
            "ERROR_SOMETHING_NEW" to R.string.error_generic,
        )
        for ((code, message) in codes) {
            assertEquals(code, context.getString(message), FirebaseAuthException(code, "raw").friendlyMessage(context))
        }
    }

    @Test
    fun `a slow, signed-out or busy garden says so, and anything else is a plain apology`() {
        fun read(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException(code.name, code).friendlyMessage(context)
        assertEquals(context.getString(R.string.error_offline), read(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
        assertEquals(context.getString(R.string.error_auth_recent_login), read(FirebaseFirestoreException.Code.UNAUTHENTICATED))
        assertEquals(context.getString(R.string.error_server_busy), read(FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))
        assertEquals(context.getString(R.string.error_offline), FirebaseNetworkException("offline").friendlyMessage(context))
        assertEquals(context.getString(R.string.error_generic), IllegalStateException("boom").friendlyMessage(context))
        assertEquals(context.getString(R.string.error_name_required), FriendlyException(R.string.error_name_required).friendlyMessage(context))
        // Words that are only spaces are no words.
        assertEquals(context.getString(R.string.error_unavailable), shown("   ", Code.INTERNAL))
    }

    @Test
    fun `a time under a minute ago, or unknown, is just now`() {
        val now = System.currentTimeMillis()
        assertEquals(context.getString(R.string.time_just_now), relativeTime(context, 0L))
        assertEquals(context.getString(R.string.time_just_now), relativeTime(context, now - 30_000L))
        assertNotEquals(context.getString(R.string.time_just_now), relativeTime(context, now - 2 * 3600_000L))
    }
}
