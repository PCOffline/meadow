package dev.pcoffline.meadow.work

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.PendingEmergency
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.data.UsageShareRepository
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The 15-minute sync for someone signed in, against the emulators. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncWorkerSignedInTest : EmulatorTest() {
    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
    }

    private fun runSync(): ListenableWorker.Result = onBackend { TestListenableWorkerBuilder<SyncWorker>(app as Context).build().doWork() }

    @Test
    fun `it shares what is shared, sends a heartbeat at most hourly, and succeeds`() {
        val robin = person("Robin")
        AppGraph.store.setPrivacy(Privacy(shareTime = true))

        assertEquals(ListenableWorker.Result.success(), runSync())

        val beat = AppGraph.store.lastHeartbeatMillis
        assertTrue(beat > 0)
        onBackend {
            val user = backend.user(robin.uid)
            val today = user.collection(UsageShareRepository.TIME).document(UsageShareRepository.TODAY).get().await()
            assertEquals(Days.todayKey(), today.getString("dayKey"))
            // Today's top apps aren't shared, so they aren't sent.
            assertFalse(user.collection(UsageShareRepository.APPS).document(UsageShareRepository.TODAY).get().await().exists())
        }

        // Within the hour, the next sync sends no heartbeat.
        assertEquals(ListenableWorker.Result.success(), runSync())
        assertEquals(beat, AppGraph.store.lastHeartbeatMillis)
    }

    @Test
    fun `it reports an unlock taken offline`() {
        val robin = person("Robin")
        AppGraph.store.pendingEmergency = PendingEmergency(
            15,
            "No signal on the hill",
            null,
            null,
            startedAt = System.currentTimeMillis() - 60_000L,
            ownerUid = robin.uid,
        )

        assertEquals(ListenableWorker.Result.success(), runSync())

        assertNull(AppGraph.store.pendingEmergency)
        onBackend {
            val reported = backend.user(robin.uid).collection("emergencies").get().await()
            assertEquals(true, reported.documents.single().getBoolean("offline"))
        }
    }

    @Test
    fun `signed out, it shares nothing and sends no heartbeat`() {
        AppGraph.store.lastHeartbeatMillis = 0
        assertEquals(ListenableWorker.Result.success(), runSync())
        assertEquals(0L, AppGraph.store.lastHeartbeatMillis)
    }
}
