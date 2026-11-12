package dev.pcoffline.meadow.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A version like 1.4.2, optionally with a pre-release suffix (1.5.0-beta.1). */
data class Version(val major: Int, val minor: Int, val patch: Int, val preRelease: String? = null) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(
        this, other,
        { it.major }, { it.minor }, { it.patch },
        // A release sorts after its own pre-releases.
        { it.preRelease == null }, { it.preRelease.orEmpty() },
    )

    override fun toString() = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(text: String): Version? {
            val m = pattern.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, pre) = m.destructured
            return Version(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt(), pre.ifEmpty { null })
        }
    }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
    /** "sha256:<hex>", published by GitHub for every release asset. */
    val digest: String? = null,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val pageUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

/** A newer Meadow, ready to download. */
data class AvailableUpdate(
    val version: Version,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val pageUrl: String,
)

object Releases {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): GitHubRelease = json.decodeFromString(GitHubRelease.serializer(), body)

    /**
     * Whether [url] is a file of a release of [repo] on GitHub, over HTTPS. The app only downloads
     * an update from there, whatever a release's listing names.
     */
    fun fromRepo(url: String, repo: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.path.orEmpty().startsWith("/$repo/releases/download/", ignoreCase = true)
    }

    /**
     * The update [release] offers over [current], or null if it isn't newer or has no APK. With
     * [repo], an APK counts only when it is that repository's own release file ([fromRepo]).
     */
    fun updateFrom(release: GitHubRelease, current: String, repo: String? = null): AvailableUpdate? {
        if (release.draft || release.prerelease) return null
        val offered = Version.parse(release.tag) ?: return null
        val installed = Version.parse(current) ?: return null
        if (offered <= installed) return null
        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) && (repo == null || fromRepo(it.url, repo)) }
            .minByOrNull { if (it.name.startsWith("meadow", ignoreCase = true)) 0 else 1 }
            ?: return null
        return AvailableUpdate(
            version = offered,
            notes = release.body.orEmpty().trim(),
            apkUrl = apk.url,
            sizeBytes = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
            pageUrl = release.pageUrl,
        )
    }
}
