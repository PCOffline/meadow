package dev.pcoffline.meadow.ui.screens

import android.provider.Settings
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.notifications.Notifier
import dev.pcoffline.meadow.ui.settings.UpdatesCard
import dev.pcoffline.meadow.update.FakeWeb
import dev.pcoffline.meadow.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The Updates card in Settings: each step from checking to installing, and what it says at each. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class UpdatesCardTest : ScreenTest() {
    private val apk = ByteArray(1000) { 7 }

    private val release = """
        {"tag_name": "v99.0.0", "body": "* feat: a new flower by @sam in #1",
         "assets": [{"name": "meadow.apk", "browser_download_url": "https://example.com/meadow.apk", "size": ${apk.size}, "digest": "sha256:00"}]}
    """.trimIndent().toByteArray()

    /** Gives [before] bytes of [bytes] at once, and the rest after [open]. */
    private class Gate(private val bytes: ByteArray, private val before: Int) : InputStream() {
        private val latch = CountDownLatch(1)
        private var at = 0

        fun open() = latch.countDown()

        override fun read(): Int {
            if (at == before) latch.await(10, TimeUnit.SECONDS)
            return if (at < bytes.size) bytes[at++].toInt() and 0xff else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at == before) latch.await(10, TimeUnit.SECONDS)
            if (at >= bytes.size) return -1
            val n = minOf(len, (if (at < before) before else bytes.size) - at)
            System.arraycopy(bytes, at, b, off, n)
            at += n
            return n
        }
    }

    private fun updater(web: FakeWeb) = Updater(app, { Notifier(app, AppGraph.store) }, Updater.INSTALL_TIMEOUT_MS, web::open)

    private fun waitForText(id: Int, vararg args: Any) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(id, *args))).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a check shows that it is looking, then the update and what is new in it`() {
        val answer = Gate(release, before = 0)
        val updater = updater(FakeWeb { FakeWeb.Answer(200, length = release.size.toLong(), stream = { answer }) })
        show { UpdatesCard(updater) }
        node(R.string.settings_updates_version, updater.currentVersion).assertExists()

        node(R.string.settings_updates_check).performClick()
        waitForText(R.string.settings_updates_checking)
        node(R.string.settings_updates_check).assertIsNotEnabled()

        answer.open()
        waitForText(R.string.settings_updates_available, "99.0.0")
        compose.onNodeWithText("• A new flower").assertExists()
        node(R.string.settings_updates_install).assertIsEnabled()
        node(R.string.settings_updates_check).assertDoesNotExist()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `without permission to install, Update asks for it and opens Android's setting`() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        val updater = updater(FakeWeb { FakeWeb.Answer(200, release) })
        show { UpdatesCard(updater) }
        node(R.string.settings_updates_check).performClick()
        waitForText(R.string.settings_updates_available, "99.0.0")

        node(R.string.settings_updates_install).performClick()
        node(R.string.settings_updates_allow).assertExists()
        node(R.string.settings_updates_allow_button).performClick()
        val opened = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, opened.action)
        assertEquals("package:${app.packageName}", opened.dataString)
    }

    @Test
    fun `a download shows how far it is, and a bad one says so and can be checked again`() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val download = Gate(apk, before = apk.size / 2)
        val updater = updater(
            FakeWeb { url ->
                if (url.endsWith(".apk")) FakeWeb.Answer(200, length = apk.size.toLong(), stream = { download }) else FakeWeb.Answer(200, release)
            },
        )
        show { UpdatesCard(updater) }
        node(R.string.settings_updates_check).performClick()
        waitForText(R.string.settings_updates_available, "99.0.0")

        node(R.string.settings_updates_install).performClick()
        waitForText(R.string.settings_updates_downloading, 50)
        node(R.string.settings_updates_check).assertIsNotEnabled()

        download.open()
        // The download doesn't match the checksum its release gives.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂 " + text(R.string.update_failed_checksum))).fetchSemanticsNodes().isNotEmpty() }
        node(R.string.settings_updates_check).assertIsEnabled()
    }

    @Test
    fun `up to date, and waiting for Android, each say so`() {
        val updater = updater(FakeWeb { FakeWeb.Answer(404) })
        show { UpdatesCard(updater) }
        node(R.string.settings_updates_check).performClick()
        waitForText(R.string.settings_updates_current)

        compose.runOnIdle { updater.installing(dev.pcoffline.meadow.update.AvailableUpdate(dev.pcoffline.meadow.update.Version(99, 0, 0), "", "", 0, null, "")) }
        waitForText(R.string.settings_updates_installing)
        node(R.string.settings_updates_check).assertIsNotEnabled()
    }

    @Test
    fun `the daily check is a switch that is kept`() {
        val updater = updater(FakeWeb { FakeWeb.Answer(404) })
        show { UpdatesCard(updater) }
        val before = AppGraph.store.autoUpdate.value
        node(R.string.settings_updates_auto).performClick()
        compose.runOnIdle { assertEquals(!before, AppGraph.store.autoUpdate.value) }
    }
}
