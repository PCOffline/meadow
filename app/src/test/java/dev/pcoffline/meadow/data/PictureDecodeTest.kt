package dev.pcoffline.meadow.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * A picked photo, decoded the right way up and shrunk while it's large. Android 9 and later turn
 * it themselves; on older phones Meadow reads the camera's EXIF orientation.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PictureDecodeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** A landscape JPEG, [width] by [height], that the camera says to turn by [orientation]. */
    private fun photo(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Uri {
        val file = File(app.cacheDir, "photo-$width-$orientation.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return Uri.fromFile(file)
    }

    @Test
    fun `a large photo is sampled down as it's decoded`() {
        val decoded = Pictures.decode(app, photo(1600, 800), sampleAbove = 400)
        assertEquals(400, decoded.width)
        assertEquals(200, decoded.height)
        // A small one is decoded whole.
        assertEquals(300, Pictures.decode(app, photo(300, 150), sampleAbove = 400).width)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `on an older phone, a photo taken upright is turned by its EXIF orientation`() {
        val decoded = Pictures.decode(app, photo(400, 200, ExifInterface.ORIENTATION_ROTATE_90), sampleAbove = 1000)
        assertEquals(200, decoded.width)
        assertEquals(400, decoded.height)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `on an older phone, a large photo is sampled down, and one without EXIF stays as it is`() {
        val decoded = Pictures.decode(app, photo(1600, 800), sampleAbove = 400)
        assertEquals(400, decoded.width)
        val plain = File(app.cacheDir, "plain.png").apply {
            outputStream().use { Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(120, Pictures.decode(app, Uri.fromFile(plain), sampleAbove = 400).width)
    }
}
