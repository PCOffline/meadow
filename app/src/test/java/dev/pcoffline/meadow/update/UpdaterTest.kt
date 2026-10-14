package dev.pcoffline.meadow.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.notifications.Notifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** An update handed to Android's installer must never leave Meadow waiting for ever. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class UpdaterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val update = AvailableUpdate(Version(9, 0, 0), "", "https://example.com/meadow.apk", 42, null, "https://example.com")
    private val confirm = Intent("dev.pcoffline.meadow.test.CONFIRM_INSTALL")
    private lateinit var notifier: Notifier

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().clear().commit()
        notifier = Notifier(app, LocalStore(app)).apply { createChannels() }
    }

    private fun updater(timeoutMs: Long = Updater.INSTALL_TIMEOUT_MS) = Updater(app, { notifier }, timeoutMs)

    @Test
    fun `while Meadow is on screen, Android's confirmation opens straight away`() {
        val updater = updater().apply { installing(update) }
        updater.onConfirmNeeded(confirm, inForeground = true)
        assertEquals(confirm.action, shadowOf(app).nextStartedActivity?.action)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `in the background, the confirmation waits in a notification`() {
        val updater = updater().apply { installing(update) }
        updater.onConfirmNeeded(confirm, inForeground = false)
        assertNull(shadowOf(app).nextStartedActivity)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(Notifier.CH_UPDATES, posted.channelId)
        assertEquals(confirm.action, shadowOf(posted.contentIntent).savedIntent.action)
    }

    @Test
    fun `with no answer from Android, the update is offered again`() {
        val updater = updater(timeoutMs = 50).apply { installing(update) }
        assertEquals(UpdateState.Installing(update), updater.state.value)
        val until = System.currentTimeMillis() + 5_000
        while (updater.state.value is UpdateState.Installing && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(UpdateState.Available(update), updater.state.value)
    }

    @Test
    fun `a failed or impossible confirmation stops waiting`() {
        val updater = updater().apply { installing(update) }
        updater.onConfirmNeeded(null, inForeground = true)
        assertTrue(updater.state.value is UpdateState.Failed)

        updater.installing(update)
        updater.onInstallResult(false, "Update cancelled.")
        assertEquals(UpdateState.Failed("Update cancelled.", update), updater.state.value)
    }
}
