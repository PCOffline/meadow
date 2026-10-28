package dev.pcoffline.meadow.data

import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Websites limited like apps. A site's limit is stored under the id `site:` + its domain (say
 * `site:youtube.com`), so requests, Bumblebees' answers and sharing treat it like any app. Only
 * sites someone adds are ever looked for; every other address is ignored the moment it's read.
 */
object Sites {
    const val PREFIX = "site:"

    /** Longest domain DNS allows. */
    private const val MAX_DOMAIN = 253
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    fun isSite(id: String): Boolean = id.startsWith(PREFIX)

    fun idFor(domain: String): String = PREFIX + domain

    /** The domain of a site's id, or null for an app's package. */
    fun domainOf(id: String): String? = id.takeIf(::isSite)?.removePrefix(PREFIX)

    /**
     * What someone typed to add a site, as the domain to limit: "https://www.YouTube.com/watch"
     * becomes "youtube.com". Null when it isn't a website's address. A leading "www." is dropped,
     * since the limit covers every part of the site anyway.
     */
    fun normalize(input: String): String? {
        val host = hostOf(input.trim()) ?: return null
        val domain = host.removePrefix("www.")
        return domain.takeIf(::isDomain)
    }

    /** A real domain: at least two labels, letters, digits and hyphens, no address made of numbers. */
    fun isDomain(domain: String): Boolean {
        if (domain.length > MAX_DOMAIN) return false
        val labels = domain.split('.')
        if (labels.size < 2 || !labels.all { LABEL.matches(it) }) return false
        // An IP address isn't a site someone can name; the top level is never all digits.
        return !labels.last().all(Char::isDigit)
    }

    /**
     * The host in an address bar's text, lower case and in ASCII, or null when the bar shows
     * something else (a search, a blank page, a browser's own page). Browsers often leave the
     * scheme out, so it's added when missing.
     */
    fun hostOf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        val raw = uri.host ?: uri.authority?.substringAfterLast('@')?.substringBefore(':') ?: return null
        val ascii = runCatching { IDN.toASCII(raw.trimEnd('.'), IDN.ALLOW_UNASSIGNED) }.getOrNull() ?: return null
        return ascii.lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }

    /** Whether [host] is [domain] or one of its parts: m.youtube.com is youtube.com, notyoutube.com isn't. */
    fun covers(domain: String, host: String): Boolean = host == domain || host.endsWith(".$domain")

    /**
     * Which of the limited [domains] the address bar's [text] is on, the most specific if more
     * than one fits; null for any other address.
     */
    fun match(text: String, domains: Collection<String>): String? {
        if (domains.isEmpty()) return null
        val host = hostOf(text) ?: return null
        return domains.filter { covers(it, host) }.maxByOrNull { it.length }
    }
}

/**
 * The browsers whose address bar the garden keeper can read, by the id of that bar. In any other
 * app (or browser) nothing is read for websites.
 */
object Browsers {
    private val chromium = "url_bar"
    private val firefox = "mozac_browser_toolbar_url_view"

    val addressBars: Map<String, String> = mapOf(
        "com.android.chrome" to chromium,
        "com.chrome.beta" to chromium,
        "com.chrome.dev" to chromium,
        "com.chrome.canary" to chromium,
        "org.chromium.chrome" to chromium,
        "com.brave.browser" to chromium,
        "com.microsoft.emmx" to chromium,
        "com.vivaldi.browser" to chromium,
        "com.kiwibrowser.browser" to chromium,
        "org.mozilla.firefox" to firefox,
        "org.mozilla.firefox_beta" to firefox,
        "org.mozilla.fenix" to firefox,
        "com.sec.android.app.sbrowser" to "location_bar_edit_text",
        "com.sec.android.app.sbrowser.beta" to "location_bar_edit_text",
        "com.opera.browser" to "url_field",
        "com.duckduckgo.mobile.android" to "omnibarTextInput",
    )

    /** The full view id of [pkg]'s address bar, or null when it isn't a browser Meadow can read. */
    fun addressBarOf(pkg: String, extra: Map<String, String> = emptyMap()): String? =
        (addressBars[pkg] ?: extra[pkg])?.let { "$pkg:id/$it" }
}
