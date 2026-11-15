package dev.pcoffline.meadow.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.enforcement.GuardianService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The sync that WorkManager runs every 15 minutes. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncWorkerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
    }

    @Test
    fun `with no backend, it restarts the guardian and succeeds without sharing`() {
        val worker = TestListenableWorkerBuilder<SyncWorker>(app as Context).build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        val started = shadowOf(app).nextStartedService
        assertEquals(GuardianService::class.java.name, started?.component?.className)
    }

    @Test
    fun `today's summary is for today, and with no usage it is empty`() {
        val summary = SyncWorker.todaySummary()

        assertEquals(Days.todayKey(), summary.dayKey)
        assertEquals(0, summary.totalMinutes)
        assertTrue(summary.apps.isEmpty())
    }

    @Test
    fun `scheduling twice keeps one periodic sync`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)

        SyncWorker.schedule(app)
        SyncWorker.schedule(app)

        val works = WorkManager.getInstance(app).getWorkInfosForUniqueWork("meadow-sync").get()
        assertEquals(1, works.size)
        assertEquals(WorkInfo.State.ENQUEUED, works.single().state)
    }
}
