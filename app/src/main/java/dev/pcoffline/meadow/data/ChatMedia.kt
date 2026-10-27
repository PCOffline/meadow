package dev.pcoffline.meadow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import dev.pcoffline.meadow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Photos and GIFs in chats. They live in the chat's own folder in Storage, which only its two
 * people can read, and are fetched with their sign-in rather than a shareable link. This phone
 * keeps a copy of each it has shown, in its cache, until signing out.
 */
class ChatMedia(private val context: Context) {
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val downloads = Mutex()

    /** A picture ready to send. */
    class Prepared(val bytes: ByteArray, val width: Int, val height: Int, val gif: Boolean)

    /**
     * Readies [uri] to send. A GIF goes as it is, so it still moves; anything else becomes a JPEG
     * at most [MAX_SIDE] pixels across. Re-encoding also drops its metadata, such as where a
     * photo was taken.
     */
    suspend fun prepare(uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val head = resolver.openInputStream(uri)?.use { it.readUpTo(6) } ?: throw FriendlyException(R.string.error_photo)
        if (isGif(resolver.getType(uri), head)) {
            val bytes = resolver.openInputStream(uri)?.use { it.readUpTo(MAX_BYTES + 1) } ?: throw FriendlyException(R.string.error_photo)
            if (bytes.size > MAX_BYTES) throw FriendlyException(R.string.chat_gif_too_big)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw FriendlyException(R.string.error_photo)
            return@withContext Prepared(bytes, bounds.outWidth, bounds.outHeight, gif = true)
        }
        val bitmap = decode(uri)
        val (width, height) = scaledSize(bitmap.width, bitmap.height, MAX_SIDE)
        val scaled = if (width == bitmap.width) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        Prepared(bytes, width, height, gif = false)
    }

    /** Uploads [picture] to my folder in [chatId], returning where it went. */
    suspend fun upload(uid: String, chatId: String, picture: Prepared): String {
        val path = pathFor(chatId, uid, UUID.randomUUID().toString().replace("-", ""), picture.gif)
        val ref = storage.reference.child(path)
        val type = if (picture.gif) "image/gif" else "image/jpeg"
        Trace.request<UploadTask.TaskSnapshot>("upload ${ref.path} (${picture.bytes.size} bytes)", describe = { "${it.bytesTransferred} bytes sent" }) {
            ref.putBytes(picture.bytes, StorageMetadata.Builder().setContentType(type).build()).await()
        }
        cacheFile(path).apply { parentFile?.mkdirs() }.writeBytes(picture.bytes)
        return path
    }

    /** This phone's copy of [path], fetched the first time. Null once it's gone (its chat closed). */
    suspend fun file(path: String): File? = downloads.withLock {
        val file = cacheFile(path)
        if (file.exists()) return@withLock file
        val bytes = try {
            download(path)
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) return@withLock null
            throw e
        }
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        file
    }

    /** The picture at [path], straight from Storage. */
    suspend fun download(path: String): ByteArray =
        Trace.request("download $path", describe = { "${it.size} bytes" }) { storage.reference.child(path).getBytes(MAX_BYTES.toLong()).await() }

    private fun cacheFile(path: String) = File(cacheDir(context), sha256(path))

    private fun decode(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE * 2) decoder.setTargetSampleSize(longest / (MAX_SIDE * 2))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / (MAX_SIDE * 2)).coerceAtLeast(1)
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw FriendlyException(R.string.error_photo)
    }

    companion object {
        /** Longest side of a photo as sent: plenty for a phone screen. */
        const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 82

        /** Must stay under the limit in storage.rules (5 MB). */
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val DIR = "chat-media"

        /** Where a picture goes: the chat's folder, then the sender's, as storage.rules requires. */
        fun pathFor(chatId: String, uid: String, id: String, gif: Boolean) = "chats/$chatId/$uid/$id.${if (gif) "gif" else "jpg"}"

        /** Whether a picture is a GIF, by its type or else by its first bytes ("GIF8"). */
        fun isGif(mimeType: String?, head: ByteArray): Boolean =
            mimeType == "image/gif" || (head.size >= 4 && String(head, 0, 4, Charsets.US_ASCII) == "GIF8")

        /** [width] by [height] shrunk to fit [max] on its longest side, never enlarged. */
        fun scaledSize(width: Int, height: Int, max: Int): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= max) return width to height
            val scale = max.toDouble() / longest
            return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
        }

        private fun cacheDir(context: Context) = File(context.cacheDir, DIR)

        /** At most [limit] bytes from the start of the stream (readNBytes needs Android 13). */
        internal fun InputStream.readUpTo(limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(minOf(limit, 64 * 1024).coerceAtLeast(1))
            while (out.size() < limit) {
                val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        /** Forgets every chat picture this phone kept, e.g. on signing out. */
        fun clearCache(context: Context) {
            cacheDir(context).deleteRecursively()
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
