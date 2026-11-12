package dev.pcoffline.meadow.usage

/**
 * The app on screen when a read of the usage log starts. [UsageTracker] reads a stretch from a few
 * hours before it, to find an app that was open as the stretch began. An app on one screen for
 * longer than that has no event in the read, so it wasn't counted at all (#159).
 *
 * This finds that app once for each start, from a longer read before the start, and puts it at
 * the front of the events. It only does so for a stretch that ends now: the budgets read today,
 * this week and each cap again and again, from a few starts. A past day in the statistics keeps
 * the shorter read.
 */
internal class OpenAt(private val read: (from: Long, to: Long) -> List<ScreenEvent>) {
    private val known = LinkedHashMap<Long, String?>()

    /** [events], read from [from], with the app on screen at [from] put first when there was one. */
    @Synchronized
    fun withOpenApp(from: Long, to: Long, now: Long, events: List<ScreenEvent>): List<ScreenEvent> {
        // A start that is still close to now may not have all its events in the log yet.
        if (to < now - ENDS_NOW_MS || from > now - SETTLED_MS) return events
        val app = if (from in known) {
            known[from]
        } else {
            ForegroundTime.foregroundAfter(null, read(from - LONGER_MS, from)).also {
                known[from] = it
                while (known.size > KEPT) known.remove(known.keys.first())
            }
        }
        return app?.let { listOf(ScreenEvent(from, ScreenEvent.Kind.RESUMED, it)) + events } ?: events
    }

    companion object {
        /** How much further back to look: with the tracker's 3 hours, a whole day. */
        const val LONGER_MS = 21 * 60 * 60_000L

        /** A stretch whose end is this close to now ends now. */
        const val ENDS_NOW_MS = 60_000L

        /** A start must be this far back before its answer is kept. */
        const val SETTLED_MS = 10 * 60_000L

        /** Starts kept: today, this week, and a few caps. */
        const val KEPT = 8
    }
}
