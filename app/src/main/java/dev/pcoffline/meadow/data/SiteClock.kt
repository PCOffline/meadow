package dev.pcoffline.meadow.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Time on one limited site, from [start] to [end] (both epoch millis). */
@Serializable
data class SiteSession(val site: String, val start: Long, val end: Long)

/**
 * Measures time on the limited websites, since Android's usage log only knows apps. The garden
 * keeper calls [seen] while a limited site is in front, with the screen on, at least every
 * [GAP_MS]; a longer silence (the screen went off, the browser was left, Meadow was stopped) ends
 * the visit where it was last seen, so time is never counted that wasn't watched. Only the
 * limited sites' own time is kept, on this phone, for [KEEP_DAYS] days.
 */
class SiteClock(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("meadow_sites", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var sessions: List<SiteSession> = read<List<SiteSession>>(KEY).orEmpty()

    /** The visit being measured: its site, when it started and when it was last seen. */
    private var open: SiteSession? = null
    private var openSavedAt = 0L

    init {
        // A visit Meadow was stopped in the middle of counts up to when it was last seen.
        read<SiteSession>(KEY_OPEN)?.let { close(it) }
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    /** [site] (a site's id) is in front now, with the screen on. */
    @Synchronized
    fun seen(site: String) {
        val at = now()
        val current = open
        open = when {
            current == null -> SiteSession(site, at, at)
            current.site != site || at - current.end > GAP_MS || at < current.end -> {
                close(current)
                SiteSession(site, at, at)
            }
            else -> current.copy(end = at)
        }
        // Kept as it goes, so a visit isn't lost if Meadow is stopped before it ends.
        if (at - openSavedAt >= SAVE_EVERY_MS || open?.start == at) {
            prefs.edit { putString(KEY_OPEN, json.encodeToString(open)) }
            openSavedAt = at
        }
    }

    /** No limited site is in front any more: the visit ends now, if it was seen just before. */
    @Synchronized
    fun left() {
        val current = open ?: return
        val at = now()
        close(if (at - current.end in 0..GAP_MS) current.copy(end = at) else current)
        open = null
    }

    /** The site being measured right now, if any. */
    @Synchronized
    fun current(): String? = open?.takeIf { now() - it.end in 0..GAP_MS }?.site

    /** Time on each site between [from] and [to], the visit going on now included. */
    @Synchronized
    fun usageBetween(from: Long, to: Long): Map<String, Long> {
        if (to <= from) return emptyMap()
        val totals = HashMap<String, Long>()
        val live = open?.let { if (now() - it.end in 0..GAP_MS) it.copy(end = now()) else it }
        for (s in sessions + listOfNotNull(live)) {
            val overlap = minOf(s.end, to) - maxOf(s.start, from)
            if (overlap > 0) totals[s.site] = (totals[s.site] ?: 0L) + overlap
        }
        return totals
    }

    /** Forgets everything, as when the account is deleted. */
    @Synchronized
    fun clear() {
        open = null
        sessions = emptyList()
        prefs.edit { clear() }
    }

    private fun close(session: SiteSession) {
        val oldest = now() - KEEP_DAYS * DAY_MS
        if (session.end > session.start) sessions = sessions + session
        sessions = sessions.filter { it.end > oldest }
        prefs.edit {
            putString(KEY, json.encodeToString(sessions))
            remove(KEY_OPEN)
        }
    }

    companion object {
        /** The garden keeper checks a site on screen at least this often. */
        const val GAP_MS = 20_000L
        const val KEEP_DAYS = 8L
        private const val DAY_MS = 24 * 60 * 60_000L
        private const val SAVE_EVERY_MS = 30_000L
        private const val KEY = "sessions"
        private const val KEY_OPEN = "open"
    }
}
