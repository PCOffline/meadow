package dev.pcoffline.meadow.enforcement

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.notifications.Notifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Each limited app on screen has a watcher: it reminds before the time runs out, covers the app
 * when it does, and does nothing while the screen is off or an emergency unlock lasts.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WatcherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.notifier.createChannels()
        manager.cancelAll()
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        service.listWindows = { emptyList() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(app).clearNextStartedActivities()
        BlockActivity.markShown("")
        BlockActivity.showing = null
    }

    @After
    fun tearDown() {
        controller.destroy()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        AppGraph.store.clearAll()
    }

    private fun warnings() = shadowOf(manager).allNotifications.filter { it.channelId == Notifier.CH_WARNINGS }

    private fun blockScreens() = generateSequence { shadowOf(app).nextStartedActivity }
        .filter { it.component?.className == BlockActivity::class.java.name }.toList()

    private fun waitUntil(timeoutMs: Long = 5_000, done: () -> Boolean): Boolean {
        val main = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!done()) {
            if (System.currentTimeMillis() > deadline) return false
            main.idle()
            Thread.sleep(10)
        }
        return true
    }

    @Test
    fun `with a few minutes left, the watcher reminds`() {
        AppGraph.store.setLimits(listOf(AppLimit(APP, "Reels", 14, 0)))
        service.onForeground(APP)
        assertTrue(waitUntil { warnings().isNotEmpty() })
        assertTrue(blockScreens().isEmpty())
    }

    @Test
    fun `during an emergency unlock a resting app is neither covered nor reminded about`() {
        AppGraph.store.setEmergency(Emergency(untilMillis = System.currentTimeMillis() + 30 * 60_000L, packageName = APP))
        AppGraph.store.setLimits(listOf(AppLimit(APP, "Reels", 0, 0, schedule = Schedule((1..7).associateWith { emptyList<TimeWindow>() }))))
        service.onForeground(APP)
        // Long enough for a watcher to have looked once.
        waitUntil(timeoutMs = 1_500) { false }
        assertTrue(blockScreens().isEmpty())
        assertEquals(0, warnings().size)
    }

    @Test
    fun `with the screen off, nothing is checked`() {
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        AppGraph.store.setLimits(listOf(AppLimit(APP, "Reels", 14, 0)))
        service.onForeground(APP)
        // Long enough for a watcher to have looked once.
        waitUntil(timeoutMs = 1_500) { false }
        assertEquals(0, warnings().size)
    }

    private companion object {
        const val APP = "com.example.reels"
    }
}
