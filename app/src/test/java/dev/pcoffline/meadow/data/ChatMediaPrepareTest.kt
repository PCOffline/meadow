package dev.pcoffline.meadow.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** A picture made ready to send: photos shrink to JPEGs, GIFs go as they are, and kept pictures are read from the phone. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ChatMediaPrepareTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val media = ChatMedia(app)

    private fun file(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(app.cacheDir, name).apply { writeBytes(bytes) })

    private fun failure(uri: Uri): Int? =
        (runCatching { runBlocking { media.prepare(uri) } }.exceptionOrNull() as? FriendlyException)?.messageRes

    @Test
    fun `a large photo is sent as a JPEG that fits the longest side`() {
        val photo = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val png = java.io.ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val prepared = runBlocking { media.prepare(file("photo.png", png)) }
        assertFalse(prepared.gif)
        assertEquals(ChatMedia.MAX_SIDE, prepared.width)
        assertEquals(1200, prepared.height)
        // A JPEG starts with FF D8.
        assertEquals(0xFF.toByte(), prepared.bytes[0])
        assertEquals(0xD8.toByte(), prepared.bytes[1])
    }

    @Test
    fun `a GIF is sent as it is, with its size`() {
        val gif = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
        val prepared = runBlocking { media.prepare(file("one.gif", gif)) }
        assertTrue(prepared.gif)
        assertEquals(1 to 1, prepared.width to prepared.height)
        assertArrayEquals(gif, prepared.bytes)
    }

    @Test
    fun `a GIF over the limit, or one that can't be read, is refused`() {
        val big = "GIF89a".toByteArray() + ByteArray(ChatMedia.MAX_BYTES)
        assertEquals(R.string.chat_gif_too_big, failure(file("big.gif", big)))
        assertEquals(R.string.error_photo, failure(file("broken.gif", "GIF89a not a picture".toByteArray())))
    }

    @Test
    fun `a kept picture is read from the phone, until the pictures are forgotten`() {
        val path = "chats/a_b/a/0123456789abcdef.jpg"
        val name = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        val kept = File(File(app.cacheDir, "chat-media"), name).apply { parentFile?.mkdirs(); writeText("picture") }
        assertEquals(kept, runBlocking { media.file(path) })

        ChatMedia.clearCache(app)
        assertFalse(kept.exists())
    }
}
