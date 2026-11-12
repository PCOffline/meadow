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

    // "feat(chat)!: open at what's new": the kind of change, and what it says.
    private val titled = Regex("""^(\w+)(?:\([^)]*\))?!?:\s+(.+)$""")

    // GitHub ends each line of its notes with who made the change, and where.
    private val credit = Regex("""\s+by @\S+ in \S+$""")
    private val bullet = Regex("""^[*-]\s+""")

    /** The kinds of change that people see or feel. The rest (build, ci, docs, test…) is left out. */
    private val forPeople = setOf("feat", "fix", "perf")

    /**
     * A release's notes as people read them in Settings. GitHub writes them for developers: a
     * heading, each change with its kind and author, and a link to the full list. This keeps what
     * changed for people, a line each, and leaves out the rest. Empty when nothing is left.
     */
    fun notesForPeople(body: String): String = body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("**") && !it.startsWith("<!--") }
        .map { it.replace(bullet, "").replace(credit, "") }
        // "@sam made their first contribution in …"
        .filterNot { it.startsWith("@") }
        .mapNotNull { line ->
            val match = titled.matchEntire(line) ?: return@mapNotNull line
            val (kind, summary) = match.destructured
            // A plain title with a colon ("Rest screen: read the usage log once") isn't a kind.
            when {
                kind.lowercase() in forPeople -> summary
                kind.lowercase() in notForPeople -> null
                else -> line
            }
        }
        .map { "• " + it.replaceFirstChar(Char::uppercase) }
        .joinToString("\n")

    private val notForPeople = setOf("build", "ci", "chore", "docs", "test", "refactor", "style", "revert")

    fun parse(body: String): GitHubRelease = json.decodeFromString(GitHubRelease.serializer(), body)

    /** The update [release] offers over [current], or null if it isn't newer or has no APK. */
    fun updateFrom(release: GitHubRelease, current: String): AvailableUpdate? {
        if (release.draft || release.prerelease) return null
        val offered = Version.parse(release.tag) ?: return null
        val installed = Version.parse(current) ?: return null
        if (offered <= installed) return null
        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .minByOrNull { if (it.name.startsWith("meadow", ignoreCase = true)) 0 else 1 }
            ?: return null
        return AvailableUpdate(
            version = offered,
            notes = notesForPeople(release.body.orEmpty()),
            apkUrl = apk.url,
            sizeBytes = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
            pageUrl = release.pageUrl,
        )
    }
}
