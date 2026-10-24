package dev.pcoffline.meadow.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.enforcement.AppBudget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Reminders are gentle: one at five minutes left and one at one minute, once each per day. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private lateinit var notifier: Notifier

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().clear().commit()
        notifier = Notifier(app, LocalStore(app)).apply { createChannels() }
    }

    private fun budget(usedMinutes: Double, daily: Int = 30, emergencyUntil: Long = 0L) = AppBudget(
        AppLimit("demo.app", "Demo", daily, 0), (usedMinutes * 60_000).toLong(), 0L, 0, 0, emergencyUntil,
    )

    private fun posted() = shadowOf(manager).allNotifications.size

    @Test
    fun `channels exist for every kind of message, including updates`() {
        val ids = manager.notificationChannels.map { it.id }.toSet()
        assertEquals(
            setOf(Notifier.CH_GUARDIAN, Notifier.CH_WARNINGS, Notifier.CH_REQUESTS, Notifier.CH_DECISIONS, Notifier.CH_CHAT,
                Notifier.CH_SOCIAL, Notifier.CH_ALERTS, Notifier.CH_UPDATES),
            ids,
        )
    }

    @Test
    fun `nothing is said while there's plenty of time`() {
        notifier.maybeWarn(budget(usedMinutes = 10.0))
        assertEquals(0, posted())
    }

    @Test
    fun `five minutes and one minute left are each mentioned once`() {
        // Every reminder for an app shares one notification, so each is cleared before the next check.
        notifier.maybeWarn(budget(usedMinutes = 25.5)) // 4.5 minutes left
        assertEquals(1, posted())
        manager.cancelAll()
        notifier.maybeWarn(budget(usedMinutes = 26.0))
        assertEquals(0, posted())
        notifier.maybeWarn(budget(usedMinutes = 29.5)) // Under a minute left: a new reminder.
        assertEquals(1, posted())
        assertEquals(true, shadowOf(manager).allNotifications.single().extras.getCharSequence("android.title")!!.contains("Demo"))
        manager.cancelAll()
        notifier.maybeWarn(budget(usedMinutes = 29.8))
        assertEquals(0, posted())
    }

    @Test
    fun `extra time from a Bumblebee gets its own reminders`() {
        notifier.maybeWarn(budget(usedMinutes = 25.5))
        manager.cancelAll()
        // 15 more minutes were granted and are nearly used up too.
        val extended = AppBudget(AppLimit("demo.app", "Demo", 30, 0), (40.5 * 60_000).toLong(), 0L, 15, 0)
        notifier.maybeWarn(extended)
        assertEquals(1, posted())
    }

    @Test
    fun `no reminders during an emergency unlock or once time is up`() {
        notifier.maybeWarn(budget(usedMinutes = 27.0, emergencyUntil = System.currentTimeMillis() + 60_000))
        notifier.maybeWarn(budget(usedMinutes = 30.0))
        assertEquals(0, posted())
    }

    private fun chatPush(to: String?): Map<String, String> = buildMap {
        put("type", "chat")
        put("chatId", "chat-1")
        put("title", "Bee")
        put("body", "Hello")
        if (to != null) put("to", to)
    }

    @Test
    fun `a push for the signed-in account is shown`() {
        notifier.fromPush(chatPush(to = "sam"), signedInUid = "sam")
        assertEquals(1, posted())
        assertEquals("Bee", shadowOf(manager).allNotifications.single().extras.getCharSequence("android.title").toString())
    }

    @Test
    fun `a push for an account that used to be signed in here is dropped`() {
        notifier.fromPush(chatPush(to = "sam"), signedInUid = "alex")
        assertEquals(0, posted())
    }

    @Test
    fun `a push arriving while signed out is dropped`() {
        notifier.fromPush(chatPush(to = "sam"), signedInUid = null)
        assertEquals(0, posted())
    }

    @Test
    fun `a push that doesn't say who it is for is dropped`() {
        notifier.fromPush(chatPush(to = null), signedInUid = "sam")
        assertEquals(0, posted())
    }

    @Test
    fun `an update is offered once with a way to open settings`() {
        notifier.updateAvailable("1.2.0")
        val notification = shadowOf(manager).getNotification(Notifier.UPDATE_ID)
        assertEquals(true, notification.extras.getCharSequence("android.title")!!.contains("1.2.0"))
        assertEquals(true, notification.contentIntent != null)
    }
}
