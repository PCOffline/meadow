package dev.pcoffline.meadow.data

/**
 * What Bumblebees see of the person they look after, decided in one place. The upload and the
 * preview in Settings both go through it, so a privacy choice added here shows in the preview
 * the moment it applies to what's sent. The security rules hold the same line on the backend.
 */
object Sharing {
    /** Today's screen time as Bumblebees get it: [today] is only read when it's shared. */
    fun usage(privacy: Privacy, today: () -> SharedUsage): SharedUsage? = if (privacy.shareUsage) today() else null

    /**
     * The person's page as their Bumblebees see it: only the name card of their profile (the rest
     * is never readable by others), their limits, and today's time if they share it.
     */
    fun page(profile: UserProfile, today: () -> SharedUsage, limits: List<AppLimit>): SharedPage = SharedPage(
        profile = UserProfile(profile.uid, profile.displayName, profile.avatar, profile.bio),
        usage = usage(profile.privacy, today),
        limits = limits.sortedBy { it.appName.lowercase() },
    )
}

/** Someone's page as a Bumblebee sees it. */
data class SharedPage(val profile: UserProfile, val usage: SharedUsage?, val limits: List<AppLimit>)
