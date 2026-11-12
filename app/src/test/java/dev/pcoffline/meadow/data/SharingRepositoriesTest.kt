package dev.pcoffline.meadow.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import dev.pcoffline.meadow.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * What a person shares with their Bumblebees, their profile and their emergency unlocks, against
 * the emulators.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SharingRepositoriesTest : EmulatorTest() {
    private val share by lazy { UsageShareRepository(backend) }
    private val profiles by lazy { ProfileRepository(app, backend, store, share, sync = { error("Not used here") }) }
    private val emergency by lazy { EmergencyRepository(app, backend, store) }

    private val today = SharedUsage(DAY, 95, listOf(SharedAppUsage(APP, "Pebbles", 50)))
    private val progress = Progress(weekChange = -10, calmerWeeks = 2, keptDays = 5, limitDays = 7)

    @Test
    fun `a Bumblebee sees each part that is shared, and nothing of the rest`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        val chosen = Privacy(shareTime = true, shareProgress = true, shareLimits = true)
        onBackend {
            LimitsRepository(backend, store, RequestRepository(backend) { null }).save(AppLimit(APP, "Pebbles", 30, 0))
            profiles.setPrivacy(chosen)
            share.share(chosen, { today }, { progress })
        }
        assertEquals(chosen, store.privacy.value)

        signInAs(bee)
        onBackend {
            assertEquals(95, share.minutes(robin.uid, DAY).firstWhere { it != null })
            // Another day's total isn't today's.
            assertNull(share.minutes(robin.uid, "2026-11-11").first())
            assertNull(share.apps(robin.uid, DAY).first())
            assertEquals(progress, share.progress(robin.uid).firstWhere { it != null })
            assertEquals(listOf("Pebbles"), share.limits(robin.uid).firstWhere { it != null }!!.map { it.appName })
        }

        // Turned off, the time is taken down: the Bumblebee can't read it, nor what was there.
        signInAs(robin)
        onBackend { profiles.setPrivacy(chosen.copy(shareTime = false)) }
        signInAs(bee)
        onBackend {
            assertNull(share.minutes(robin.uid, DAY).first())
            assertEquals(progress, share.progress(robin.uid).firstWhere { it != null })
        }
    }

    @Test
    fun `a part that isn't shared is never read or sent, and a shared one can be taken down`() {
        val robin = person("Robin")
        onBackend {
            share.share(Privacy(), { error("Today isn't shared") }, { error("Progress isn't shared") })
            assertNull(share.minutes(robin.uid, DAY).first())

            share.share(Privacy(shareApps = true), { today }, { error("Progress isn't shared") })
            assertEquals(today.apps, share.apps(robin.uid, DAY).firstWhere { it != null })
            share.takeDown(Privacy())
            share.apps(robin.uid, DAY).firstWhere { it == null }
        }
    }

    @Test
    fun `the heartbeat starts once, then moves on`() {
        person("Robin")
        onBackend {
            share.heartbeat()
            share.heartbeat()
        }
        onBackend { backend.auth.signOut() }
        // Signed out, there's nothing to tell.
        onBackend { share.heartbeat() }
    }

    @Test
    fun `a second alert within two minutes is refused`() {
        val robin = person("Robin")
        onBackend {
            share.reportProtectionLost()
            // The backend notes when it told Bumblebees; until then a second alert could still pass.
            waitFor { account(robin.uid).getTimestamp("lastAlertAt") != null }
            val refused = runCatching { share.reportProtectionLost() }.exceptionOrNull()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (refused as FirebaseFirestoreException).code)
        }
    }

    @Test
    fun `a profile keeps a trimmed name and bio, and needs a name`() {
        val robin = person("Robin")
        onBackend {
            profiles.update("  Robin Hood  ", "🌻", "#FFAA00", "  ${"b".repeat(ProfileRepository.BIO_MAX + 5)}  ")
            val doc = backend.user(robin.uid).get().await()
            assertEquals("Robin Hood", doc.getString("displayName"))
            assertEquals("🌻", doc.getString("flower"))
            assertEquals(ProfileRepository.BIO_MAX, doc.getString("bio")!!.length)
        }
        val blank = assertThrows(FriendlyException::class.java) { onBackend { profiles.update(" ", "🌻", "#FFAA00", "") } }
        assertEquals(R.string.error_name_required, blank.messageRes)
    }

    @Test
    fun `appearance and accepted terms are kept on the phone and on the account`() {
        val robin = person("Robin")
        onBackend {
            profiles.setAppearance("forest", "dark")
            profiles.acceptTerms(7)
            val account = account(robin.uid)
            assertEquals("forest", account.getString("theme"))
            assertEquals("dark", account.getString("darkMode"))
            assertEquals(7L, (account.get("acceptedTerms") as Map<*, *>)["version"])
        }
        assertEquals("forest", store.theme.value)
        assertEquals(7, store.acceptedTermsVersion)

        // Signed out, they're kept on the phone only.
        onBackend {
            backend.auth.signOut()
            profiles.setAppearance("meadow", "light")
            profiles.acceptTerms(8)
        }
        assertEquals("meadow", store.theme.value)
        assertEquals(8, store.acceptedTermsVersion)
    }

    @Test
    fun `an emergency unlock needs a choice of time and a reason`() {
        person("Robin")
        val minutes = assertThrows(FriendlyException::class.java) { onBackend { emergency.unlock(20, "A good long reason", null, null) } }
        assertEquals(R.string.error_emergency_minutes, minutes.messageRes)
        val reason = assertThrows(FriendlyException::class.java) { onBackend { emergency.unlock(15, "  too short  ", null, null) } }
        assertEquals(R.string.error_emergency_reason, reason.messageRes)
    }

    @Test
    fun `an emergency unlock online tells the Bumblebee, ends early, and is rationed`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        online(true)

        val result = onBackend { emergency.unlock(15, "  I need the map  ", null, null) }
        assertFalse(result.offline)
        assertTrue(result.untilMillis > System.currentTimeMillis())
        assertTrue(store.emergency.value!!.isActive())

        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        onBackend {
            // The reason is told trimmed, in the chat with each Bumblebee.
            ChatRepository(backend).messages(chatId).firstWhere { list -> list.any { it.kind == MessageKind.SYSTEM && "I need the map" in it.text } }

            emergency.end()
            assertFalse(store.emergency.value!!.isActive())
            assertTrue(account(robin.uid).getTimestamp("emergencyUntil")!!.toDate().time <= System.currentTimeMillis())

            // Three in 30 days: the fourth is refused.
            repeat(2) {
                emergency.unlock(15, "Another real reason", null, null)
                emergency.end()
            }
        }
        val fourth = assertThrows(Backend.CallFailed::class.java) { onBackend { emergency.unlock(15, "Another real reason", null, null) } }
        assertFalse(fourth.unreachable)
    }

    @Test
    fun `offline, one emergency unlock is taken, kept, and reported once back`() {
        val robin = person("Robin")
        online(false)
        store.lastOfflineEmergencyMillis = 0
        store.pendingEmergency = null

        val result = onBackend { emergency.unlock(30, "No signal on the hill", APP, "Pebbles") }
        assertTrue(result.offline)
        assertFalse(emergency.canUnlockOffline())
        val pending = checkNotNull(store.pendingEmergency)
        assertEquals(robin.uid, pending.ownerUid)
        val again = assertThrows(FriendlyException::class.java) { onBackend { emergency.unlock(15, "No signal on the hill", null, null) } }
        assertEquals(R.string.error_emergency_offline_used, again.messageRes)

        // Ended early while still offline: the report says when.
        onBackend { emergency.end() }
        assertNotNull(store.pendingEmergency!!.endedAt)

        online(true)
        onBackend {
            emergency.flushPending()
            assertNull(store.pendingEmergency)
            val reported = backend.user(robin.uid).collection("emergencies").orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            val record = reported.documents.single()
            assertEquals(true, record.getBoolean("offline"))
            assertEquals("No signal on the hill", record.getString("reason"))
            // Nothing left to report.
            emergency.flushPending()
        }
    }

    @Test
    fun `an offline unlock is reported only for the garden that took it`() {
        person("Robin")
        store.pendingEmergency = PendingEmergency(15, "No signal on the hill", null, null, startedAt = System.currentTimeMillis(), ownerUid = "someone-else")
        online(true)
        onBackend { emergency.flushPending() }
        assertNotNull(store.pendingEmergency)
        store.pendingEmergency = null
    }

    private fun online(validated: Boolean) {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
    }

    private suspend fun waitFor(condition: suspend () -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Waited ${WAIT_MS / 1000} seconds" }
            delay(200)
        }
    }

    private companion object {
        const val APP = "com.example.pebbles"
        const val DAY = "2026-11-12"
    }
}
