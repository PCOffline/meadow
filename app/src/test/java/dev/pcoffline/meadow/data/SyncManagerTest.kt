package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The signed-in person's profile, limits and grants, mirrored onto the phone from the emulators. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncManagerTest : EmulatorTest() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth by lazy { AuthRepository(backend, store) }
    private val sync by lazy { SyncManager(backend, auth, store, scope).also { it.start() } }

    @After
    fun stopSync() = scope.cancel()

    @Test
    fun `signed out there is no profile, and signed in it is mirrored`() {
        onBackend { sync.profile.firstWhere { it == ProfileState.SignedOut } }

        person("Robin")
        onBackend {
            val ready = sync.profile.firstWhere { it is ProfileState.Ready } as ProfileState.Ready
            assertEquals("Robin", ready.profile.displayName)
            assertEquals("Robin", sync.currentProfile?.displayName)
            sync.accountConfirmed.firstWhere { it }
        }

        onBackend {
            backend.auth.signOut()
            sync.profile.firstWhere { it == ProfileState.SignedOut }
        }
    }

    @Test
    fun `the account's theme comes to the phone once, when sync starts`() {
        val robin = person("Robin")
        onBackend { backend.account(robin.uid).update(mapOf("theme" to "forest", "darkMode" to "dark")).await() }
        onBackend {
            sync.accountConfirmed.firstWhere { it }
            store.theme.firstWhere { it == "forest" }
            assertEquals("dark", store.darkMode.value)

            // Later changes on the account don't override this phone's choice.
            store.setAppearance("meadow", "light")
            backend.account(robin.uid).update("theme", "dusk").await()
            delay(SETTLE_MS)
            assertEquals("meadow", store.theme.value)
        }
    }

    @Test
    fun `an account without a garden reads as missing`() {
        onBackend {
            auth.signUp(newEmail(), PASSWORD)
            sync.profile.firstWhere { it == ProfileState.Missing }
        }
    }

    @Test
    fun `while the account is being deleted, its vanishing garden isn't missing`() {
        onBackend {
            sync.profile.firstWhere { it == ProfileState.SignedOut }
            sync.beginLeaving()
            auth.signUp(newEmail(), PASSWORD)
            // Give the listener time to answer; it must not say the garden is missing.
            delay(SETTLE_MS)
            assertTrue(sync.profile.value != ProfileState.Missing)

            sync.cancelLeaving()
            sync.retry()
            sync.profile.firstWhere { it == ProfileState.Missing }
        }
    }

    @Test
    fun `limits, Bumblebees and grants follow the account onto the phone`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        val requests = RequestRepository(backend) { null }
        val id = onBackend {
            sync.profile.firstWhere { it is ProfileState.Ready }
            LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0))
            store.limits.firstWhere { list -> list.any { it.packageName == APP } }
            store.approverCount.firstWhere { it == 1 }
            requests.create(RequestType.EXTRA_TIME, "Finishing a message", APP, "Pebbles", minutes = 15)
        }

        signInAs(bee)
        onBackend { requests.decide(id, approve = true, minutes = 15) }

        signInAs(robin)
        onBackend {
            val grant = store.grants.firstWhere { it.isNotEmpty() }.single()
            assertEquals(APP, grant.packageName)
            assertEquals(15, grant.minutes)
        }
    }

    @Test
    fun `this phone's push id is kept once, with only the newest few`() {
        val robin = person("Robin")
        onBackend { sync.profile.firstWhere { it is ProfileState.Ready } }
        val ids = (1..12).map { "phone-$it" }
        ids.forEach { id ->
            sync.onPushRegistered(id)
            onBackend { waitForPushIds(robin.uid) { id in it } }
        }
        sync.onPushRegistered("phone-12")
        onBackend { assertEquals(ids.takeLast(10), pushIds(robin.uid)) }
    }

    private suspend fun pushIds(uid: String): List<String> = account(uid).stringList("pushIds")

    private suspend fun waitForPushIds(uid: String, done: (List<String>) -> Boolean) {
        val until = System.currentTimeMillis() + WAIT_MS
        while (!done(pushIds(uid))) {
            check(System.currentTimeMillis() < until) { "The push ids never matched" }
            delay(100)
        }
    }

    private companion object {
        const val APP = "com.example.pebbles"
        const val SETTLE_MS = 3_000L
    }
}
