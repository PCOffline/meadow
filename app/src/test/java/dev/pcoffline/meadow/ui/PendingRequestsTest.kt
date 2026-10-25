package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.ui.requests.waitsAbout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which of my questions still blocks asking about an app again (one per app at a time). */
class PendingRequestsTest {
    private val now = 1_800_000_000_000L

    private fun request(type: RequestType = RequestType.EXTRA_TIME, status: RequestStatus = RequestStatus.PENDING, ageMs: Long = 60_000L) =
        LimitRequest(
            id = "r1", requesterId = "me", requesterName = "Me", type = type, packageName = "app", appName = "App",
            minutes = 15, dailyMinutes = 0, weeklyMinutes = 0, message = "", status = status, approverIds = listOf("bee"),
            decidedByName = null, decisionNote = null, dayKey = "2027-01-15", createdAtMillis = now - ageMs,
        )

    @Test
    fun `a waiting question of any kind blocks another about the same app`() {
        assertTrue(request().waitsAbout("app", now))
        assertTrue(request(type = RequestType.CHANGE_LIMIT).waitsAbout("app", now))
        assertTrue(request(type = RequestType.REMOVE_LIMIT).waitsAbout("app", now))
    }

    @Test
    fun `other apps, answered questions and expired ones don't`() {
        assertFalse(request().waitsAbout("other", now))
        assertFalse(request(status = RequestStatus.APPROVED).waitsAbout("app", now))
        assertFalse(request(status = RequestStatus.CANCELLED).waitsAbout("app", now))
        // Past its time it has expired, even before the backend settles it.
        assertFalse(request(ageMs = RequestStatus.TTL_MS + 1).waitsAbout("app", now))
    }
}
