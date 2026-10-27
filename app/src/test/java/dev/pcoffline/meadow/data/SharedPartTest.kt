package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A part a friend shares shows once the server agrees; one they stop sharing reads as not shared. */
// Robolectric: the Firestore error's codes need Android's classes.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SharedPartTest {
    private val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    /** A snapshot, as far as [UsageShareRepository.sharedPart] cares. */
    private data class Snap(val value: Int, val fromCache: Boolean)

    private fun part(listen: () -> Flow<Snap>) =
        UsageShareRepository.sharedPart("Test part", listen, { it.fromCache }) { it.value }

    @Test
    fun `a refusal is recognised on its own and inside the listener's cancellation`() {
        assertSame(denied, UsageShareRepository.refusal(denied))
        val once = CancellationException("Error getting snapshot").apply { initCause(denied) }
        assertSame(denied, UsageShareRepository.refusal(once))
        assertSame(denied, UsageShareRepository.refusal(CancellationException("again").apply { initCause(once) }))
    }

    @Test
    fun `anything else isn't taken for a refusal`() {
        assertNull(UsageShareRepository.refusal(IllegalStateException("bug")))
        assertNull(UsageShareRepository.refusal(CancellationException("left the screen")))
    }

    @Test
    fun `the cached copy waits for the server, and the server's word is shown even when it agrees`() = runTest {
        val shown = part {
            flow {
                emit(Snap(1, fromCache = true))
                // What a listener with metadata changes sends when the server confirms the cache.
                emit(Snap(1, fromCache = false))
                emit(Snap(2, fromCache = false))
            }
        }.toList()
        assertEquals(listOf(1, 2), shown)
    }

    @Test
    fun `a part the server refuses reads as not shared`() = runTest {
        // As the SDK's listener ends: its producer cancelled with the refusal as the cause.
        val refused = part {
            callbackFlow {
                trySend(Snap(1, fromCache = true))
                cancel("Error getting snapshot", denied)
                awaitClose()
            }
        }
        assertNull(refused.first())
    }

    @Test
    fun `once refused, it's asked again and shows what's shared again`() = runTest {
        var listens = 0
        val shown = part {
            flow {
                if (++listens == 1) throw CancellationException("Error getting snapshot").apply { initCause(denied) }
                emit(Snap(3, fromCache = false))
                awaitCancellation()
            }
        }.take(2).toList()
        assertEquals(listOf(null, 3), shown)
    }
}
