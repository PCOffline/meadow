package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.notifications.Notifier
import dev.pcoffline.meadow.ui.durationText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** The foreground service that keeps Meadow alive and says how much time is left. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class GuardianServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var controller: ServiceController<GuardianService>? = null

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        // The notifier skips text it already posted, and it outlives each test.
        AppGraph.notifier.updateGuardian("")
    }

    @After
    fun stop() {
        controller?.destroy()
    }

    private fun create(): GuardianService = Robolectric.buildService(GuardianService::class.java).also { controller = it }.create().get()

    /** The guardian's ongoing notification text, once [done] (its loop runs on another thread). */
    private fun awaitGuardianText(done: (String?) -> Boolean): String? {
        val end = System.currentTimeMillis() + 5_000
        var text: String? = null
        while (System.currentTimeMillis() < end) {
            text = shadowOf(manager).getNotification(Notifier.GUARDIAN_ID)?.extras?.getCharSequence("android.text")?.toString()
            if (done(text)) return text
            Thread.sleep(20)
        }
        return text
    }

    @Test
    fun `it starts in the foreground with a quiet ongoing notification`() {
        val service = create()

        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(app.getString(R.string.guardian_starting), notification.extras.getCharSequence("android.text").toString())
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notifier.GUARDIAN_ID, shadowOf(service).lastForegroundNotificationId)
    }

    @Test
    fun `it keeps running after the system stops it, and nothing binds to it`() {
        val service = create()

        assertEquals(Service.START_STICKY, service.onStartCommand(Intent(), 0, 1))
        assertNull(service.onBind(Intent()))
    }

    @Test
    fun `with no limits, its notification says to plant one`() {
        create()

        val expected = app.getString(R.string.guardian_no_limits)
        assertEquals(expected, awaitGuardianText { it == expected })
    }

    @Test
    fun `during an emergency unlock, its notification says limits are paused`() {
        AppGraph.store.setEmergency(Emergency(untilMillis = System.currentTimeMillis() + 30 * 60_000L, packageName = null))

        create()

        val paused = app.getString(R.string.guardian_emergency, "").trim()
        val text = awaitGuardianText { it.orEmpty().startsWith(paused) }
        assertTrue("$text", text.orEmpty().startsWith(paused))
    }

    @Test
    fun `start asks Android to run it in the foreground`() {
        GuardianService.start(app)

        assertEquals(GuardianService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    private val asleep = Schedule((1..7).associateWith { emptyList<TimeWindow>() })

    @Test
    fun `with time left, its notification names the app with the least`() {
        AppGraph.store.setLimits(listOf(AppLimit("com.pebbles", "Pebbles", 30, 0), AppLimit("com.ducks", "Ducks", 90, 0)))
        create()

        val expected = app.getString(R.string.guardian_left, "Pebbles", app.resources.durationText(30 * 60_000L))
        assertEquals(expected, awaitGuardianText { it == expected })
    }

    @Test
    fun `apps outside their hours are counted as resting`() {
        AppGraph.store.setLimits(listOf(AppLimit("com.pebbles", "Pebbles", 0, 0, schedule = asleep)))
        create()

        val expected = app.resources.getQuantityString(R.plurals.guardian_resting, 1, 1)
        assertEquals(expected, awaitGuardianText { it == expected })
    }

    @Test
    fun `time left and resting apps are said together`() {
        AppGraph.store.setLimits(listOf(AppLimit("com.pebbles", "Pebbles", 0, 0, schedule = asleep), AppLimit("com.ducks", "Ducks", 45, 0)))
        create()

        val left = app.getString(R.string.guardian_left, "Ducks", app.resources.durationText(45 * 60_000L))
        val expected = app.getString(R.string.guardian_joined, left, app.resources.getQuantityString(R.plurals.guardian_resting, 1, 1))
        assertEquals(expected, awaitGuardianText { it == expected })
    }

    /** [pkg] came to the front a moment ago, as the usage log tells it. The log keeps it for the other tests here too. */
    private fun inFront(pkg: String) {
        shadowOf(app.getSystemService(UsageStatsManager::class.java))
            .addEvent(pkg, System.currentTimeMillis() - 1_000, UsageEvents.Event.ACTIVITY_RESUMED)
    }

    private fun waitUntil(done: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (done()) return true
            Thread.sleep(20)
        }
        return false
    }

    @Test
    fun `without the accessibility service, a resting app in front gets its rest screen from the usage log`() {
        ShadowSettings.setCanDrawOverlays(true)
        AppGraph.store.setLimits(listOf(AppLimit("com.reels", "Reels", 0, 0, schedule = asleep)))
        inFront("com.reels")
        create()
        assertTrue(waitUntil { shadowOf(app).nextStartedActivity?.component?.className == BlockActivity::class.java.name })
    }

    @Test
    fun `without drawing over apps, a notification asks to rest it instead`() {
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        AppGraph.notifier.createChannels()
        AppGraph.store.setLimits(listOf(AppLimit("com.reels", "Reels", 0, 0, schedule = asleep)))
        inFront("com.reels")
        create()
        val title = app.getString(R.string.notify_time_up_title, "Reels")
        assertTrue(waitUntil { shadowOf(manager).allNotifications.any { it.extras.getCharSequence("android.title")?.toString() == title } })
    }

    @Test
    fun `an app in front with a few minutes left is reminded about`() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        AppGraph.notifier.createChannels()
        AppGraph.store.setLimits(listOf(AppLimit("com.reels", "Reels", 14, 0)))
        inFront("com.reels")
        create()
        assertTrue(waitUntil { shadowOf(manager).allNotifications.any { it.channelId == Notifier.CH_WARNINGS } })
    }
}
