package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Emergency
import dev.pcoffline.meadow.notifications.Notifier
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
}
