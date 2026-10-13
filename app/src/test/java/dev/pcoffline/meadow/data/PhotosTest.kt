package dev.pcoffline.meadow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Someone's photo link must never send their circle's phones to another server. */
class PhotosTest {
    private val bucket = "meadow-1.firebasestorage.app"
    private val origin = Photos.STORAGE_ORIGIN

    @Test
    fun `photos in Meadow's own Storage load`() {
        assertTrue(Photos.isOwnPhoto("$origin/v0/b/$bucket/o/avatars%2Fsam?alt=media&token=1f2e#v=17", bucket, origin))
        assertTrue(Photos.isOwnPhoto("http://10.0.2.2:9199/v0/b/demo-meadow.appspot.com/o/avatars%2Fsam?alt=media", "demo-meadow.appspot.com", "http://10.0.2.2:9199"))
    }

    @Test
    fun `links anywhere else don't`() {
        listOf(
            "https://example.com/a.jpg",
            "$origin/v0/b/attacker.appspot.com/o/avatars%2Fsam?alt=media",
            "$origin/v0/b/$bucket/o/exports%2Fsam%2Fmeadow-1.json?alt=media",
            "https://firebasestorage.googleapis.com.evil.dev/v0/b/$bucket/o/avatars%2Fsam",
            "http://firebasestorage.googleapis.com/v0/b/$bucket/o/avatars%2Fsam",
            "$origin@evil.dev/v0/b/$bucket/o/avatars%2Fsam",
        ).forEach { assertFalse(it, Photos.isOwnPhoto(it, bucket, origin)) }
        // Without a known bucket (no Firebase config), nothing loads.
        assertFalse(Photos.isOwnPhoto("$origin/v0/b//o/avatars%2Fsam", "", origin))
    }
}
