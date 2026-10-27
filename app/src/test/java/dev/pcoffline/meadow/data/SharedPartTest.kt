package dev.pcoffline.meadow.data

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A part a friend stops sharing reads as not shared, however the refusal arrives. */
class SharedPartTest {
    private val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `a refusal is recognised on its own and inside the listener's cancellation`() {
        assertSame(denied, UsageShareRepository.refusal(denied))
        assertSame(denied, UsageShareRepository.refusal(CancellationException("Error getting snapshot").apply { initCause(denied) }))
    }

    @Test
    fun `anything else isn't taken for a refusal`() {
        assertNull(UsageShareRepository.refusal(IllegalStateException("bug")))
        assertNull(UsageShareRepository.refusal(CancellationException("left the screen")))
    }
}
