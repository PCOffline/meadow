package dev.pcoffline.meadow.data

/**
 * What Bumblebees see of the person they look after, decided in one place. The upload and the
 * preview in Settings both go through it, so a privacy choice added here shows in the preview
 * the moment it applies to what's sent. The security rules hold the same line on the backend,
 * part by part.
 */
object Sharing {
    /** Today's screen time, while it's shared. */
    fun minutes(privacy: Privacy, today: SharedUsage): Int? = today.totalMinutes.takeIf { privacy.shareTime }

    /** Today's top apps, while they're shared; their limits only while limits are. */
    fun apps(privacy: Privacy, today: SharedUsage): List<SharedAppUsage>? =
        if (!privacy.shareApps) null
        else today.apps.map { if (privacy.shareLimits) it else it.copy(limitMinutes = 0) }

    /** Limits, while they're shared. */
    fun limits(privacy: Privacy, limits: List<AppLimit>): List<AppLimit>? =
        limits.sortedBy { it.appName.lowercase() }.takeIf { privacy.shareLimits }

    /**
     * The person's page as their Bumblebees see it: only the name card of their profile (the rest
     * is never readable by others), and each part they share. [today] and [progress] are only
     * read for parts that are shared.
     */
    fun page(profile: UserProfile, today: () -> SharedUsage, progress: () -> Progress, limits: List<AppLimit>): SharedPage {
        val privacy = profile.privacy
        val usage = if (privacy.shareTime || privacy.shareApps) today() else null
        return SharedPage(
            profile = UserProfile(profile.uid, profile.displayName, profile.avatar, profile.bio),
            minutes = usage?.let { minutes(privacy, it) },
            apps = usage?.let { apps(privacy, it) },
            progress = if (privacy.shareProgress) progress() else null,
            limits = limits(privacy, limits),
        )
    }
}

/** Someone's page as a Bumblebee sees it. Each part is null while it isn't shared. */
data class SharedPage(
    val profile: UserProfile,
    val minutes: Int?,
    val apps: List<SharedAppUsage>?,
    val progress: Progress?,
    val limits: List<AppLimit>?,
)
