package dev.pcoffline.meadow.data

import androidx.annotation.VisibleForTesting
import com.google.firebase.storage.FirebaseStorage
import dev.pcoffline.meadow.BuildConfig

/**
 * Profile photos are only ever loaded from Meadow's own Storage. A link to anywhere else would
 * tell that server whenever someone in the person's circle saw the photo (and from where).
 */
object Photos {
    private val bucket: String by lazy { runCatching { FirebaseStorage.getInstance().reference.bucket }.getOrDefault("") }

    private val origin: String
        get() = BuildConfig.EMULATOR_HOST.takeIf { it.isNotEmpty() }?.let { "http://$it:9199" } ?: STORAGE_ORIGIN

    /**
     * One more address that loads: a photo the pull request pictures draw on the phone. Those
     * builds have no Storage, so no photo would show. It's null in the app.
     */
    @VisibleForTesting
    internal var picturesPhoto: String? = null

    /** [url] if it's safe to load, otherwise null (the emoji shows instead). */
    fun safe(url: String?): String? = url?.takeIf { isOwnPhoto(it, bucket, origin) || it == picturesPhoto }

    const val STORAGE_ORIGIN = "https://firebasestorage.googleapis.com"

    /** Whether [url] is an avatar in [bucket], served from [origin]. */
    fun isOwnPhoto(url: String, bucket: String, origin: String): Boolean =
        bucket.isNotEmpty() && url.startsWith("$origin/v0/b/$bucket/o/avatars%2F")
}
