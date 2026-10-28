package dev.pcoffline.meadow.data

import dev.pcoffline.meadow.data.ChatMedia.Companion.readUpTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

/** Chat pictures: where they go, which are GIFs, how far photos shrink, and what an export takes along. */
class ChatMediaTest {
    @Test
    fun `a picture goes in the chat's folder, then the sender's, as the rules require`() {
        val path = ChatMedia.pathFor("chat1", "robin", "0123456789abcdef", gif = false)
        assertEquals("chats/chat1/robin/0123456789abcdef.jpg", path)
        assertEquals("chats/chat1/robin/0123456789abcdef.gif", ChatMedia.pathFor("chat1", "robin", "0123456789abcdef", gif = true))
        // The same pattern as firestore.rules' imageMessage().
        assertTrue(Regex("chats/chat1/robin/[A-Za-z0-9_-]{8,64}[.](jpg|gif)").matches(path))
    }

    @Test
    fun `a picture's note is named after its path, as the rules require`() {
        val id = ChatMedia.noteIdFor("chats/a1_b2/a1/0123456789abcdef.jpg")
        assertEquals("chats~a1_b2~a1~0123456789abcdef.jpg", id)
        // storage.rules looks the note up by a URL it doesn't escape, so the id must need no escaping.
        assertEquals("/$id", URI(null, null, "/$id", null).rawPath)
    }

    @Test
    fun `a GIF is known by its type or by its first bytes`() {
        val gif = "GIF89a".toByteArray()
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertTrue(ChatMedia.isGif("image/gif", png))
        assertTrue(ChatMedia.isGif(null, gif))
        assertTrue(ChatMedia.isGif("application/octet-stream", gif))
        assertFalse(ChatMedia.isGif("image/png", png))
        assertFalse(ChatMedia.isGif(null, "GIF".toByteArray()))
    }

    @Test
    fun `photos shrink to fit their longest side and are never enlarged`() {
        assertEquals(1600 to 1200, ChatMedia.scaledSize(4000, 3000, 1600))
        assertEquals(900 to 1600, ChatMedia.scaledSize(1080, 1920, 1600))
        assertEquals(800 to 600, ChatMedia.scaledSize(800, 600, 1600))
        // A sliver stays at least a pixel wide.
        assertEquals(1 to 1600, ChatMedia.scaledSize(1, 10_000, 1600))
    }

    @Test
    fun `reading stops at the limit, however the stream hands out its bytes`() {
        val bytes = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(bytes.copyOf(6), ByteArrayInputStream(bytes).readUpTo(6))
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readUpTo(500_000))
        // A stream that gives a few bytes at a time still fills up to the limit.
        val trickle = object : InputStream() {
            private var at = 0
            override fun read() = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= bytes.size) return -1
                val n = minOf(len, 7, bytes.size - at)
                bytes.copyInto(b, off, at, at + n)
                at += n
                return n
            }
        }
        assertArrayEquals(bytes.copyOf(100_001), trickle.readUpTo(100_001))
    }

    @Test
    fun `an export takes along each chat picture once, and nothing else`() {
        val json = """
            {
              "profile": { "path": "avatars/robin" },
              "conversations": [
                { "id": "c1", "messages": [
                  { "kind": "text", "text": "Hi", "path": "not/a/picture.jpg" },
                  { "kind": "image", "path": "chats/c1/robin/aaaaaaaa.jpg" },
                  { "kind": "image", "path": "chats/c1/maya/bbbbbbbb.gif" },
                  { "kind": "image", "path": "chats/c1/robin/aaaaaaaa.jpg" },
                  { "kind": "image", "path": 12 }
                ] },
                { "id": "c2", "messages": [] }
              ]
            }
        """.trimIndent()
        assertEquals(listOf("chats/c1/robin/aaaaaaaa.jpg", "chats/c1/maya/bbbbbbbb.gif"), ProfileRepository.picturePaths(json))
        assertEquals(emptyList<String>(), ProfileRepository.picturePaths("""{ "profile": {} }"""))
        assertEquals(emptyList<String>(), ProfileRepository.picturePaths("not json"))
    }
}
