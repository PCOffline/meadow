package dev.pcoffline.meadow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream

/** Your own profile, appearance, privacy choices and data rights. */
class ProfileRepository(
    private val context: Context,
    private val backend: Backend,
    private val store: LocalStore,
    private val sync: () -> SyncManager,
) {
    private val storage by lazy { FirebaseStorage.getInstance() }

    suspend fun update(displayName: String, emoji: String, color: String, bio: String) {
        if (displayName.isBlank()) throw FriendlyException(dev.pcoffline.meadow.R.string.error_name_required)
        val uid = backend.requireUid()
        backend.user(uid).update(
            mapOf(
                "displayName" to displayName.trim().take(30),
                "flower" to emoji,
                "avatarColor" to color,
                "bio" to bio.trim().take(BIO_MAX),
            ),
        ).awaitWrite()
    }

    /** Crops the picked image to a small square, uploads it, and makes it your avatar. */
    suspend fun setPhoto(uri: Uri) {
        val uid = backend.requireUid()
        val bytes = withContext(Dispatchers.IO) { squareJpeg(uri) }
        val ref = storage.reference.child("avatars/$uid")
        Trace.request<UploadTask.TaskSnapshot>("upload ${ref.path} (${bytes.size} bytes)", describe = { "${it.bytesTransferred} bytes sent" }) {
            ref.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        }
        // The fragment changes the image cache key so the new photo shows straight away.
        val url = Trace.request("download URL of ${ref.path}") { ref.downloadUrl.await() }.toString() + "#v=" + System.currentTimeMillis()
        backend.user(uid).update("photoUrl", url).awaitWrite()
    }

    suspend fun removePhoto() {
        val uid = backend.requireUid()
        backend.user(uid).update("photoUrl", null).awaitWrite()
        runCatchingCancellable { storage.reference.child("avatars/$uid").delete().await() }
    }

    /** Applies straight away on this phone and follows you to others. */
    suspend fun setAppearance(theme: String, darkMode: String) {
        store.setAppearance(theme, darkMode)
        val uid = backend.uid ?: return
        runCatchingCancellable { backend.account(uid).update(mapOf("theme" to theme, "darkMode" to darkMode)).awaitWrite() }
    }

    /** Updates what Bumblebees can see. Turning usage sharing off also removes what was already shared. */
    suspend fun setPrivacy(privacy: Privacy) {
        val uid = backend.requireUid()
        store.setPrivacy(privacy)
        backend.account(uid).update("privacy", mapOf("shareUsage" to privacy.shareUsage)).awaitWrite()
        if (!privacy.shareUsage) {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                val shared = backend.user(uid).collection("usage").get().await()
                val batch = backend.db.batch()
                shared.documents.forEach { batch.delete(it.reference) }
                batch.commit().awaitWrite()
            }
        }
    }

    /** Records that the person accepted [version] of the terms and privacy notice. */
    suspend fun acceptTerms(version: Int) {
        store.acceptedTermsVersion = version
        val uid = backend.uid ?: return
        runCatchingCancellable {
            backend.account(uid).update("acceptedTerms", mapOf("version" to version, "at" to FieldValue.serverTimestamp())).awaitWrite()
        }
    }

    /**
     * Everything the backend holds about you, as pretty-printed JSON. The backend writes it to
     * your own private folder; it's fetched once and then deleted there.
     */
    suspend fun exportData(): String {
        val result = backend.call("exportMyData", timeoutSeconds = 130)
        val path = result["path"] as? String ?: throw FriendlyException(dev.pcoffline.meadow.R.string.error_generic)
        val ref = storage.reference.child(path)
        val bytes = Trace.request<ByteArray>("download ${ref.path}", describe = { "${it.size} bytes" }) { ref.getBytes(MAX_EXPORT_BYTES).await() }
        runCatchingCancellable { ref.delete().await() }
        val data = Json.parseToJsonElement(bytes.decodeToString())
        return PrettyJson.encodeToString(JsonElement.serializer(), data)
    }

    /** Deletes the account and everything tied to it, then forgets it on this phone too. */
    suspend fun deleteAccount() {
        // The profile disappears mid-call; keep the app from offering to create a new one.
        sync().beginLeaving()
        try {
            backend.call("deleteAccount", timeoutSeconds = 310)
        } catch (e: Exception) {
            sync().cancelLeaving()
            throw e
        }
        runCatchingCancellable { backend.auth.signOut() }
        store.clearAll()
        context.filesDir.resolve("usage_history.json").delete()
    }

    private fun squareJpeg(uri: Uri): ByteArray {
        val source = decode(uri)
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, PHOTO_SIZE, PHOTO_SIZE, true)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DECODE) decoder.setTargetSampleSize(longest / MAX_DECODE)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / MAX_DECODE).coerceAtLeast(1)
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw FriendlyException(dev.pcoffline.meadow.R.string.error_photo)
    }

    companion object {
        const val BIO_MAX = 80
        private const val PHOTO_SIZE = 384
        private const val MAX_DECODE = 1600
        private const val MAX_EXPORT_BYTES = 32L * 1024 * 1024
        private val PrettyJson = Json { prettyPrint = true }

        /** Converts a callable result (maps, lists, primitives) into JSON. */
        fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
            is List<*> -> JsonArray(value.map(::toJson))
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
