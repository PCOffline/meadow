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
import dev.pcoffline.meadow.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import android.content.pm.PackageInstaller

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

    private fun online(web: FakeWeb) = Updater(app, { notifier }, Updater.INSTALL_TIMEOUT_MS, web::open)

    private fun release(tag: String, vararg assets: String) = """
        {"tag_name": "$tag", "body": "* feat: a new flower by @sam in #1", "html_url": "https://example.com/r",
         "assets": [${assets.joinToString()}]}
    """.trimIndent()

    private fun apkAsset(size: Long = 4, digest: String? = null) =
        """{"name": "meadow.apk", "browser_download_url": "https://example.com/meadow.apk", "size": $size${digest?.let { ", \"digest\": \"$it\"" } ?: ""}}"""

    /** Waits for the install that [updater] started to fail or reach Android's installer. */
    private fun settled(updater: Updater): UpdateState {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            val state = updater.state.value
            if (state is UpdateState.Failed || state is UpdateState.Installing) return state
            Thread.sleep(10)
        }
        return updater.state.value
    }

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

    @Test
    fun `a download stops at the size its release gives`() {
        val bytes = ByteArray(200_000) { 1 }
        val whole = java.io.ByteArrayOutputStream()
        assertEquals(200_000L, Updater.copyUpTo(bytes.inputStream(), whole, limit = 200_000))
        assertEquals(200_000, whole.size())

        val cut = java.io.ByteArrayOutputStream()
        val failure = runCatching { Updater.copyUpTo(bytes.inputStream(), cut, limit = 100_000) }.exceptionOrNull()
        assertTrue("$failure", failure is dev.pcoffline.meadow.data.FriendlyException)
        // Nothing past the limit was written.
        assertTrue("${cut.size()}", cut.size() <= 100_000)
    }

    @Test
    fun `without a size, a download has a fixed limit`() {
        assertEquals(42L, Updater.maxBytes(update.copy(sizeBytes = 42)))
        assertEquals(Updater.MAX_APK_BYTES, Updater.maxBytes(update.copy(sizeBytes = 0)))
    }

    @Test
    fun `a newer release on GitHub is offered, with its notes for people`() = runBlocking {
        val web = FakeWeb { FakeWeb.Answer(200, release("v99.0.0", apkAsset(digest = "sha256:ABCD")).toByteArray()) }
        val updater = online(web)
        val update = updater.check()
        assertNotNull(update)
        assertEquals(Version(99, 0, 0), update!!.version)
        assertEquals("abcd", update.sha256)
        assertEquals("• A new flower", update.notes)
        assertEquals(UpdateState.Available(update), updater.state.value)
        assertTrue(web.asked.single(), web.asked.single().endsWith("/releases/latest"))
    }

    @Test
    fun `the same or an older release means Meadow is up to date`() = runBlocking {
        val updater = online(FakeWeb { FakeWeb.Answer(200, release("v0.0.1", apkAsset()).toByteArray()) })
        assertNull(updater.check())
        assertEquals(UpdateState.UpToDate, updater.state.value)
    }

    @Test
    fun `with no releases yet, Meadow is up to date`() = runBlocking {
        val updater = online(FakeWeb { FakeWeb.Answer(HttpURLConnection.HTTP_NOT_FOUND) })
        assertNull(updater.check())
        assertEquals(UpdateState.UpToDate, updater.state.value)
    }

    @Test
    fun `an error from GitHub or no connection says to check the connection`() = runBlocking {
        for (web in listOf(FakeWeb { FakeWeb.Answer(500) }, FakeWeb { FakeWeb.Answer(0, failure = IOException("no route")) })) {
            val updater = online(web)
            assertNull(updater.check())
            assertEquals(UpdateState.Failed(app.getString(R.string.error_offline), null), updater.state.value)
        }
    }

    @Test
    fun `an answer Meadow can't read says the check failed`() = runBlocking {
        val updater = online(FakeWeb { FakeWeb.Answer(200, "not json".toByteArray()) })
        assertNull(updater.check())
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_check), null), updater.state.value)
    }

    @Test
    fun `a check during a download or an install keeps that update`() = runBlocking {
        val web = FakeWeb { FakeWeb.Answer(200, release("v99.0.0", apkAsset()).toByteArray()) }
        val updater = online(web)
        updater.installing(update)
        assertEquals(update, updater.check())
        assertEquals(UpdateState.Installing(update), updater.state.value)
        assertTrue(web.asked.isEmpty())
    }

    @Test
    fun `a download that doesn't match its checksum isn't installed, and its file is checked`() {
        val apk = "not an apk".toByteArray()
        val web = FakeWeb { FakeWeb.Answer(200, apk) }
        val updater = online(web)
        updater.startInstall(update.copy(sizeBytes = apk.size.toLong(), sha256 = "00"))
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_checksum), update.copy(sizeBytes = apk.size.toLong(), sha256 = "00")), settled(updater))
        assertEquals(listOf(update.apkUrl), web.asked)
        assertTrue(File(app.cacheDir, "updates/meadow-9.0.0.apk").exists())
    }

    @Test
    fun `a download that isn't an app isn't installed`() {
        val apk = "not an apk".toByteArray()
        val file = File(app.cacheDir, "check.apk").apply { writeBytes(apk) }
        val right = update.copy(sizeBytes = apk.size.toLong(), sha256 = Updater.sha256(file))
        val updater = online(FakeWeb { FakeWeb.Answer(200, apk) })
        updater.startInstall(right)
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_checksum), right), settled(updater))
    }

    @Test
    fun `a download larger than its release says isn't installed`() {
        val updater = online(FakeWeb { FakeWeb.Answer(200, ByteArray(100), length = -1) })
        val small = update.copy(sizeBytes = 10)
        updater.startInstall(small)
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_checksum), small), settled(updater))
    }

    @Test
    fun `a download that fails says to check the connection`() {
        val updater = online(FakeWeb { FakeWeb.Answer(503) })
        updater.startInstall(update)
        assertEquals(UpdateState.Failed(app.getString(R.string.error_offline), update), settled(updater))
    }

    @Test
    fun `Android decides whether Meadow may install, and its setting opens on Meadow`() {
        val intent = updater().installPermissionIntent()
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
        // Android's setting decides whether Meadow may install.
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(updater().canInstall())
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(updater().canInstall())
    }

    @Test
    fun `a finished install clears the downloaded file and the notification`() {
        val updater = updater().apply { installing(update) }
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        File(dir, "meadow.apk").writeText("apk")
        notifier.confirmUpdate(confirm)
        updater.onInstallResult(true, null)
        assertFalse(dir.exists())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertEquals(UpdateState.Installing(update), updater.state.value)
    }

    @Test
    fun `without a message, a failed install says Android can't install it`() {
        val updater = updater().apply { installing(update) }
        updater.onInstallResult(false, null)
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_install), update), updater.state.value)
    }

    @Test
    fun `the installer's answers reach the updater`() {
        dev.pcoffline.meadow.AppGraph.init(app)
        val updater = dev.pcoffline.meadow.AppGraph.updater
        val receiver = InstallResultReceiver()
        fun answer(status: Int, extra: Intent? = null) = receiver.onReceive(
            app,
            Intent().putExtra(PackageInstaller.EXTRA_STATUS, status).apply { extra?.let { putExtra(Intent.EXTRA_INTENT, it) } },
        )

        updater.installing(update)
        answer(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(UpdateState.Failed(app.getString(R.string.update_cancelled), update), updater.state.value)

        updater.installing(update)
        answer(PackageInstaller.STATUS_FAILURE)
        assertEquals(UpdateState.Failed(app.getString(R.string.update_failed_install), update), updater.state.value)

        updater.installing(update)
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION)
        assertTrue(updater.state.value is UpdateState.Failed)

        updater.installing(update)
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, confirm)
        assertEquals(UpdateState.Installing(update), updater.state.value)

        answer(PackageInstaller.STATUS_SUCCESS)
        assertEquals(UpdateState.Installing(update), updater.state.value)
    }
}
