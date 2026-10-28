package dev.pcoffline.meadow.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Photos from older phones, whose camera only notes which way is up, arrive upright. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PicturesTest {
    /** 2 wide, 1 tall: red on the left, blue on the right. */
    private fun landscape(): Bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, Color.RED)
        setPixel(1, 0, Color.BLUE)
    }

    @Test
    fun `every EXIF orientation turns the right way`() {
        mapOf(
            ExifInterface.ORIENTATION_UNDEFINED to (0f to false),
            ExifInterface.ORIENTATION_NORMAL to (0f to false),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to (0f to true),
            ExifInterface.ORIENTATION_ROTATE_180 to (180f to false),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to (180f to true),
            ExifInterface.ORIENTATION_TRANSPOSE to (90f to true),
            ExifInterface.ORIENTATION_ROTATE_90 to (90f to false),
            ExifInterface.ORIENTATION_TRANSVERSE to (270f to true),
            ExifInterface.ORIENTATION_ROTATE_270 to (270f to false),
        ).forEach { (orientation, turn) -> assertEquals("orientation $orientation", turn, Pictures.turn(orientation)) }
    }

    @Test
    fun `a photo already upright is left as it is`() {
        val photo = landscape()
        assertSame(photo, Pictures.upright(photo, ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun `a photo taken holding the phone upright is turned to stand`() {
        val turned = Pictures.upright(landscape(), ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(1, turned.width)
        assertEquals(2, turned.height)
        // A quarter turn clockwise: the left (red) ends up on top.
        assertEquals(Color.RED, turned.getPixel(0, 0))
        assertEquals(Color.BLUE, turned.getPixel(0, 1))
    }

    @Test
    fun `a mirrored photo is mirrored back`() {
        val turned = Pictures.upright(landscape(), ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertEquals(Color.BLUE, turned.getPixel(0, 0))
        assertEquals(Color.RED, turned.getPixel(1, 0))
    }
}
