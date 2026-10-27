package dev.pcoffline.meadow.data

import java.net.URI
import java.net.URLDecoder

/**
 * A sign-in link that opened in the browser (Android didn't hand it to Meadow) lands on the
 * `/signed-in` page, which offers it back as `meadow://signin?link=<the link>`.
 */
object SignInLinks {
    fun fromApp(uri: String): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != "meadow" || parsed.host != "signin") return null
        return parsed.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("link=") }
            ?.let { URLDecoder.decode(it.removePrefix("link="), "UTF-8") }
            ?.takeIf { it.startsWith("https://") }
    }
}
