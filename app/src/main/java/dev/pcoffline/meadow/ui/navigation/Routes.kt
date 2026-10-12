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

/**
 * Where a notification asks to go. Notifications carry a small string so they stay valid
 * across app versions: "requests", "circle", "chat/<id>" or "friend/<uid>".
 */
object DeepLinks {
    fun requests() = "requests"
    fun circle() = "circle"
    fun chat(chatId: String) = "chat/$chatId"
    fun friend(uid: String) = "friend/$uid"

    fun toRoute(link: String): Any? = when {
        link == "requests" -> RequestsRoute
        link == "circle" -> CircleRoute
        link.startsWith("chat/") -> link.removePrefix("chat/").takeIf { it.isNotBlank() }?.let(::ChatRoute)
        link.startsWith("friend/") -> link.removePrefix("friend/").takeIf { it.isNotBlank() }?.let(::FriendRoute)
        else -> null
    }
}
