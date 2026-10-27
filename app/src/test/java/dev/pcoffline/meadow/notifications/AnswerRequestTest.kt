package dev.pcoffline.meadow.notifications

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answering from a notification. */
class AnswerRequestTest {
    @Test
    fun `an answer given offline waits for a connection instead of using up its tries`() {
        val work = RequestActionReceiver.answer("r1", approve = true, notificationId = 7).workSpec
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertTrue(work.expedited)
        assertEquals("r1", work.input.getString(RequestActionReceiver.EXTRA_REQUEST))
        assertEquals(true, work.input.getBoolean(RequestActionReceiver.EXTRA_APPROVE, false))
        assertEquals(7, work.input.getInt(RequestActionReceiver.EXTRA_NOTIFICATION, 0))
    }
}
