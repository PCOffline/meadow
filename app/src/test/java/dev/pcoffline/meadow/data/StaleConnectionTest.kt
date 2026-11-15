package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** A Firebase Auth call sent on a connection the server had already closed is sent once more. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StaleConnectionTest {
    private val closed = FirebaseException("An internal error has occurred. [ unexpected end of stream on com.android.okhttp.Address@a62b7c16 ]")

    private fun answers(results: List<Result<String>>): Pair<() -> Task<String>, () -> Int> {
        var calls = 0
        val call = {
            val result = results[calls++]
            result.fold({ Tasks.forResult(it) }, { Tasks.forException<String>(it as Exception) })
        }
        return call to { calls }
    }

    @Test
    fun `a call sent on a closed connection is sent once more`() {
        val (call, calls) = answers(listOf(Result.failure(closed), Result.success("signed in")))
        assertEquals("signed in", runBlocking { withFreshConnection(call) })
        assertEquals(2, calls())
    }

    @Test
    fun `it is sent again only once`() {
        val (call, calls) = answers(listOf(Result.failure(closed), Result.failure(closed)))
        assertThrows(FirebaseException::class.java) { runBlocking { withFreshConnection(call) } }
        assertEquals(2, calls())
    }

    @Test
    fun `any other failure is not sent again`() {
        val (call, calls) = answers(listOf(Result.failure(FirebaseException("The password is invalid."))))
        assertThrows(FirebaseException::class.java) { runBlocking { withFreshConnection(call) } }
        assertEquals(1, calls())
    }

    @Test
    fun `a closed connection is found in what caused a failure`() {
        assertTrue(sentOnClosedConnection(FirebaseException("Network error", IOException("unexpected end of stream on Address"))))
        assertFalse(sentOnClosedConnection(FirebaseException("A network error (such as timeout) has occurred.")))
    }
}
