package dev.pcoffline.meadow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import dev.pcoffline.meadow.R

/** Pictures picked on the phone (profile photos, chat photos), decoded the right way up. */
object Pictures {
    /**
     * [uri] decoded in software, sampled down while its longest side is over [sampleAbove]. Turned
     * upright as the camera recorded it: Android 9 and later do this themselves, older phones
     * need it done from the photo's EXIF orientation, or portrait photos arrive on their side.
     */
    fun decode(context: Context, uri: Uri, sampleAbove: Int): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > sampleAbove) decoder.setTargetSampleSize(longest / sampleAbove)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / sampleAbove).coerceAtLeast(1)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw FriendlyException(R.string.error_photo)
        val exif = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return upright(bitmap, exif)
    }

    /** How an EXIF [orientation] turns a picture upright: degrees clockwise, then whether to mirror it. */
    internal fun turn(orientation: Int): Pair<Float, Boolean> = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f to false
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to true
        ExifInterface.ORIENTATION_TRANSPOSE -> 90f to true
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f to false
        ExifInterface.ORIENTATION_TRANSVERSE -> 270f to true
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f to false
        else -> 0f to false
    }

    /** [bitmap] turned upright for its EXIF [orientation]; itself when it already is. */
    internal fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val (degrees, mirror) = turn(orientation)
        if (degrees == 0f && !mirror) return bitmap
        val matrix = Matrix().apply {
            setRotate(degrees)
            if (mirror) postScale(-1f, 1f)
        }
        // Quarter turns and mirroring move whole pixels, so nothing needs smoothing.
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }
}
