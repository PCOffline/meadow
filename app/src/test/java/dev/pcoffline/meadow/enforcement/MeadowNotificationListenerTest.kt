package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotificationListenerService

/** Resting apps' notifications are hidden, without the main thread waiting on the usage log. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MeadowNotificationListenerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<MeadowNotificationListener>
    private lateinit var service: MeadowNotificationListener

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.setGrants(emptyList())
        AppGraph.store.setEmergency(null)
        AppGraph.store.setLimits(
            listOf(
                // Its allowed hour is far from now, so it is resting.
                AppLimit(RESTING, "Resting", 30, 0, schedule = hoursAwayFromNow()),
                // Nothing used yet, so it has time left.
                AppLimit(OPEN, "Open", 30, 0),
            ),
        )
        controller = Robolectric.buildService(MeadowNotificationListener::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
        AppGraph.store.setLimits(emptyList())
    }

    @Test
    fun `a resting app's new notification is hidden, others stay`() {
        val resting = post(RESTING, 1)
        val open = post(OPEN, 2)
        val free = post(FREE, 3)

        service.onNotificationPosted(resting)
        service.onNotificationPosted(open)
        service.onNotificationPosted(free)
        awaitWork()

        assertEquals(setOf(open.key, free.key), shown())
    }

    @Test
    fun `connecting hides what resting apps already show`() {
        val open = post(OPEN, 1)
        val free = post(FREE, 2)
        post(RESTING, 3)

        service.onListenerConnected()
        awaitWork()

        assertEquals(setOf(open.key, free.key), shown())
    }

    @Test
    fun `a new notification never waits for the budget on the main thread`() {
        val sbn = post(RESTING, 1)
        whileEngineBusy {
            val started = System.nanoTime()
            service.onNotificationPosted(sbn)
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("onNotificationPosted waited $tookMs ms for the engine", tookMs < BUSY_MS / 2)
            assertEquals(setOf(sbn.key), shown())
        }
        awaitWork()
        assertEquals(emptySet<String>(), shown())
    }

    @Test
    fun `connecting never waits for budgets on the main thread`() {
        post(RESTING, 1)
        whileEngineBusy {
            val started = System.nanoTime()
            service.onListenerConnected()
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("onListenerConnected waited $tookMs ms for the engine", tookMs < BUSY_MS / 2)
        }
        awaitWork()
        assertEquals(emptySet<String>(), shown())
    }

    @Test
    fun `Meadow's own notifications always stay`() {
        AppGraph.store.setLimits(AppGraph.store.limits.value + AppLimit(app.packageName, "Meadow", 0, 0, schedule = hoursAwayFromNow()))
        val own = post(app.packageName, 1)
        service.onNotificationPosted(own)
        service.onListenerConnected()
        awaitWork()
        assertEquals(setOf(own.key), shown())
    }

    @Test
    fun `an app that starts resting has only its own notifications hidden, even with time left`() {
        val open = post(OPEN, 1)
        val free = post(FREE, 2)
        service.onListenerConnected()
        awaitWork()

        MeadowNotificationListener.rest(app, OPEN)
        assertEquals(setOf(free.key), shown())
        assertTrue(open.key !in shown())
    }

    @Test
    fun `a sweep hides apps that started resting since their notifications came`() {
        service.onListenerConnected()
        awaitWork()
        val resting = post(RESTING, 1)
        val open = post(OPEN, 2)
        MeadowNotificationListener.sweepAll()
        assertEquals(setOf(open.key), shown())
        assertTrue(resting.key !in shown())
    }

    @Test
    fun `once disconnected, a sweep finds nothing to hide, and another listener leaving changes nothing`() {
        service.onListenerConnected()
        awaitWork()
        val other = Robolectric.buildService(MeadowNotificationListener::class.java).create()
        other.get().onListenerDisconnected()
        other.destroy()
        val resting = post(RESTING, 1)
        MeadowNotificationListener.sweepAll()
        assertTrue(resting.key !in shown())

        service.onListenerDisconnected()
        val later = post(RESTING, 2)
        MeadowNotificationListener.sweepAll()
        assertTrue(later.key in shown())
    }

    @Test
    fun `only a resting app's playing media is paused`() {
        val playing = android.media.session.MediaSession(app, "playing").apply {
            setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build())
            isActive = true
        }
        val paused = android.media.session.MediaSession(app, "paused").apply {
            setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PAUSED, 0, 1f).build())
        }
        val sessions = org.robolectric.Shadows.shadowOf(app.getSystemService(android.media.session.MediaSessionManager::class.java))
        sessions.addController(playing.controller)
        sessions.addController(paused.controller)
        // Nothing to pause: none of these apps rest.
        MeadowNotificationListener.pauseMedia(app, emptySet())
        MeadowNotificationListener.pauseMedia(app, setOf(OPEN))
        MeadowNotificationListener.pauseMedia(app, setOf(app.packageName))
        playing.release()
        paused.release()
    }

    private val shadow: ShadowNotificationListenerService
        get() = Shadow.extract(service)

    private fun post(pkg: String, id: Int): StatusBarNotification {
        val key = shadow.addActiveNotification(pkg, id, Notification.Builder(app, "test").build())
        return service.activeNotifications.single { it.key == key }
    }

    private fun shown(): Set<String> = service.activeNotifications.map { it.key }.toSet()

    /** Waits for the budget checks the listener started in the background. */
    private fun awaitWork() = runBlocking { service.scope.coroutineContext.job.children.toList().joinAll() }

    /** Runs [block] while another thread holds the engine's lock, as a slow usage-log read would. */
    private fun whileEngineBusy(block: () -> Unit) {
        val engine = AppGraph.engine
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread {
            synchronized(engine) {
                held.countDown()
                release.await(BUSY_MS, TimeUnit.MILLISECONDS)
            }
        }
        held.await()
        try {
            block()
        } finally {
            release.countDown()
            holder.join()
        }
    }

    private fun hoursAwayFromNow(): Schedule {
        val now = ZonedDateTime.now()
        val from = if (now.hour < 12) 20 * 60 else 2 * 60
        return Schedule((1..7).associateWith { listOf(TimeWindow(from, from + 60)) })
    }

    private companion object {
        const val RESTING = "demo.resting"
        const val OPEN = "demo.open"
        const val FREE = "demo.free"
        const val BUSY_MS = 5_000L
    }
}
