package dev.pcoffline.meadow.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Emergency unlocks against the emulators: recorded by the backend when online, kept on the
 * phone when not and reported later, and ended early either way.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EmergencyRepositoryTest : EmulatorTest() {
    private val emergency by lazy { EmergencyRepository(app, backend, store) }

    private fun failure(block: suspend () -> Unit): Int? =
        (runCatching { onBackend { block() } }.exceptionOrNull() as? FriendlyException)?.messageRes

    /** The phone's network, as Android reports it to the repository. */
    private fun network(validated: Boolean) {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
    }

    /** Online again for whatever runs next in this test JVM. */
    @After
    fun backOnline() = network(validated = true)

    @Test
    fun `an unlock needs one of the lengths offered and a reason`() {
        person("Robin")
        assertEquals(R.string.error_emergency_minutes, failure { emergency.unlock(45, "Need the map to the vet", null, null) })
        assertEquals(R.string.error_emergency_reason, failure { emergency.unlock(15, "  short  ", null, null) })
    }

    @Test
    fun `online, the backend records the unlock and says until when`() {
        val robin = person("Robin")
        network(validated = true)
        val result = onBackend { emergency.unlock(15, "Need the map to the vet", "com.maps", "Maps") }
        assertFalse(result.offline)
        assertEquals(result.untilMillis, store.emergency.value?.untilMillis)
        assertNotNull(onBackend { account(robin.uid).getTimestamp("emergencyUntil") })

        // Ended early: the account says so at once.
        onBackend { emergency.end() }
        assertTrue(store.emergency.value!!.untilMillis <= System.currentTimeMillis())
    }

    @Test
    fun `offline, an unlock is kept on the phone, once a month, and reported when back`() {
        val robin = person("Robin")
        network(validated = false)
        val result = onBackend { emergency.unlock(30, "No signal and need the map", null, null) }
        assertTrue(result.offline)
        assertEquals(robin.uid, store.pendingEmergency?.ownerUid)
        assertEquals(R.string.error_emergency_offline_used, failure { emergency.unlock(15, "Still no signal here", null, null) })

        // Ended early while still offline: the report says when.
        onBackend { emergency.end() }
        assertNotNull(store.pendingEmergency?.endedAt)

        network(validated = true)
        onBackend { emergency.flushPending() }
        assertNull(store.pendingEmergency)
        val uses = onBackend { backend.user(robin.uid).collection("emergencies").get().await() }
        assertEquals(1, uses.size())
        assertTrue(uses.documents.single().getBoolean("offline") == true)
    }

    @Test
    fun `a report kept for another account waits, and with nobody signed in nothing is sent`() {
        val robin = person("Robin")
        store.pendingEmergency = PendingEmergency(15, "No signal and need the map", null, null, startedAt = System.currentTimeMillis(), ownerUid = "someone-else")
        onBackend { emergency.flushPending() }
        assertNotNull(store.pendingEmergency)
        assertEquals(0, onBackend { backend.user(robin.uid).collection("emergencies").get().await() }.size())

        onBackend { backend.auth.signOut() }
        store.pendingEmergency = store.pendingEmergency!!.copy(ownerUid = null)
        onBackend { emergency.flushPending() }
        assertNotNull(store.pendingEmergency)
    }
}
