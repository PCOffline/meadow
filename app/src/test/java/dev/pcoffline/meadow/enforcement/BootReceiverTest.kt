package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The boot receiver is exported, so it only acts on the system's own broadcasts. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BootReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** The actions the manifest's intent filter declares for the receiver. */
    private fun manifestActions(): List<String> {
        val manifest = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/AndroidManifest.xml") }.first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val receivers = doc.getElementsByTagName("receiver")
        val boot = (0 until receivers.length).map { receivers.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") == ".enforcement.BootReceiver" }
        val actions = boot.getElementsByTagName("action")
        return (0 until actions.length).map { (actions.item(it) as Element).getAttributeNS(ANDROID, "name") }
    }

    @Test
    fun `acts on exactly the broadcasts the manifest declares`() {
        val actions = manifestActions()
        assertEquals(listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED), actions)
        actions.forEach { assertTrue(it, BootReceiver.handles(it)) }
    }

    @Test
    fun `ignores other actions and intents without one`() {
        assertFalse(BootReceiver.handles(null))
        assertFalse(BootReceiver.handles(Intent.ACTION_MAIN))
        assertFalse(BootReceiver.handles("com.example.SPOOFED_BOOT"))
    }

    @Test
    fun `an app calling the receiver directly starts nothing`() {
        BootReceiver().onReceive(app, Intent("com.example.SPOOFED_BOOT").setClass(app, BootReceiver::class.java))
        BootReceiver().onReceive(app, Intent().setClass(app, BootReceiver::class.java))
        assertNull(shadowOf(app).nextStartedService)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
