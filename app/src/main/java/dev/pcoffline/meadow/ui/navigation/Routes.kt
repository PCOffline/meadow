package dev.pcoffline.meadow.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object GardenRoute
@Serializable data object StatsRoute
@Serializable data object RequestsRoute
@Serializable data object CircleRoute
@Serializable data object ChatsRoute
@Serializable data object SetupRoute
@Serializable data object SettingsRoute
@Serializable data object PickAppRoute
@Serializable data class LimitRoute(val packageName: String)
@Serializable data class ChatRoute(val chatId: String)
@Serializable data class FriendRoute(val uid: String)
@Serializable data object ProfileRoute
@Serializable data class LegalRoute(val doc: String)
@Serializable data object TroubleshootRoute
@Serializable data object BeeStatsRoute

/**
 * Where a notification asks to go. Notifications carry a small string so they stay valid
 * across app versions: "requests", "circle", "settings", "troubleshoot", "chat/<id>",
 * "friend/<uid>" or "ask/<package>" (asks for more time on that app).
 */
object DeepLinks {
    fun requests() = "requests"
    fun circle() = "circle"
    fun settings() = "settings"
    fun chat(chatId: String) = "chat/$chatId"
    fun friend(uid: String) = "friend/$uid"
    fun troubleshoot() = "troubleshoot"
    fun ask(packageName: String) = "ask/$packageName"

    /** The app an "ask for more time" link is for, or null for any other link. */
    fun askFor(link: String): String? = link.takeIf { it.startsWith("ask/") }?.removePrefix("ask/")?.takeIf { it.isNotBlank() }

    fun toRoute(link: String): Any? = when {
        link == "requests" -> RequestsRoute
        link == "circle" -> CircleRoute
        link == "settings" -> SettingsRoute
        link == "troubleshoot" -> TroubleshootRoute
        link.startsWith("chat/") -> link.removePrefix("chat/").takeIf { it.isNotBlank() }?.let(::ChatRoute)
        link.startsWith("friend/") -> link.removePrefix("friend/").takeIf { it.isNotBlank() }?.let(::FriendRoute)
        else -> null
    }
}
