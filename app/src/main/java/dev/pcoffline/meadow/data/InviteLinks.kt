package dev.pcoffline.meadow.data

import android.net.Uri

/**
 * Invite links look like `https://<project>.web.app/i/<token>`, or `meadow://invite/<token>` when
 * opened from the landing page. People may also paste either form, or just the token.
 */
object InviteLinks {
    private val TOKEN = Regex("^[A-Za-z0-9]{16}$")

    fun tokenFrom(uri: Uri?): String? {
        if (uri == null) return null
        val candidate = when (uri.scheme) {
            "meadow" -> if (uri.host == "invite") uri.lastPathSegment else null
            "https" -> uri.pathSegments.takeIf { it.size == 2 && it[0] == "i" }?.get(1)
            else -> null
        }
        return candidate?.takeIf { TOKEN.matches(it) }
    }

    fun tokenFromText(text: String): String? {
        val trimmed = text.trim()
        if (TOKEN.matches(trimmed)) return trimmed
        return Regex("""(?:/i/|invite/)([A-Za-z0-9]{16})\b""").find(trimmed)?.groupValues?.get(1)
    }
}
