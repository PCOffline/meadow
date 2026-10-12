package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.functions.FirebaseFunctionsException.Code
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.friendlyMessage
import org.junit.Assert.assertEquals
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
        assertEquals(context.getString(R.string.error_server_missing), shown("", Code.NOT_FOUND))
        assertEquals(context.getString(R.string.error_server_refused), shown("", Code.PERMISSION_DENIED))
        assertEquals(context.getString(R.string.error_auth_recent_login), shown("", Code.UNAUTHENTICATED))
        assertEquals(context.getString(R.string.error_server_busy), shown("", Code.RESOURCE_EXHAUSTED))
        assertEquals(context.getString(R.string.error_generic), shown("", Code.INTERNAL))
        assertEquals(context.getString(R.string.error_offline), shown("", Code.UNAVAILABLE, unreachable = true))
        assertEquals(context.getString(R.string.error_offline), shown("", null, unreachable = true))
        assertEquals("That request is gone.", shown("That request is gone.", Code.NOT_FOUND))
    }
}
