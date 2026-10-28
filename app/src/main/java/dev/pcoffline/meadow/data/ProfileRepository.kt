package dev.pcoffline.meadow.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.usage.UsageHistory
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Your own profile, appearance, privacy choices and data rights. */
class ProfileRepository(
    private val context: Context,
    private val backend: Backend,
    private val store: LocalStore,
    private val share: UsageShareRepository,
    /** Fetches a chat picture for the export. */
    private val chatPicture: suspend (path: String) -> ByteArray = { AppGraph.chatMedia.download(it) },
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

    /** Updates what Bumblebees can see. Each part turned off also takes down what was already shared of it. */
    suspend fun setPrivacy(privacy: Privacy) {
        val uid = backend.requireUid()
        store.setPrivacy(privacy)
        backend.account(uid).update("privacy", privacy.toFirestore()).awaitWrite()
        share.takeDown(privacy)
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

    /**
     * [exportData] as a zip: the JSON, then each chat photo and GIF it mentions, at the same path
     * as in the JSON. Pictures that are already gone are left out.
     */
    suspend fun exportTo(out: OutputStream) {
        val json = exportData()
        withContext(Dispatchers.IO) {
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(EXPORT_JSON))
                zip.write(json.toByteArray())
                zip.closeEntry()
                for (path in picturePaths(json)) {
                    val bytes = runCatchingCancellable { chatPicture(path) }.getOrNull() ?: continue
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
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
        listOf(UsageHistory.FILE, UsageHistory.LEGACY_FILE).forEach { context.filesDir.resolve(it).delete() }
        ChatMedia.clearCache(context)
    }

    private fun squareJpeg(uri: Uri): ByteArray {
        val source = Pictures.decode(context, uri, MAX_DECODE)
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = cropped.scale(PHOTO_SIZE, PHOTO_SIZE)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    companion object {
        const val BIO_MAX = 80
        private const val PHOTO_SIZE = 384
        private const val MAX_DECODE = 1600
        private const val MAX_EXPORT_BYTES = 32L * 1024 * 1024
        private val PrettyJson = Json { prettyPrint = true }
        const val EXPORT_JSON = "meadow-data.json"

        /** The chat pictures an export mentions, each once. */
        fun picturePaths(json: String): List<String> {
            val conversations = runCatching { Json.parseToJsonElement(json).jsonObject["conversations"]?.jsonArray }.getOrNull()
            return conversations.orEmpty().flatMap { chat ->
                (chat as? JsonObject)?.get("messages")?.let { it as? JsonArray }.orEmpty().mapNotNull { message ->
                    val fields = message as? JsonObject ?: return@mapNotNull null
                    if ((fields["kind"] as? JsonPrimitive)?.content != "image") return@mapNotNull null
                    (fields["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
            }.distinct()
        }

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
