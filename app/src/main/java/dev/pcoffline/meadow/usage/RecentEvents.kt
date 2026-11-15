package dev.pcoffline.meadow.usage

/**
 * The usage log's events for a stretch that ends now, such as today so far, kept between reads.
 *
 * Meadow measures today's usage every few seconds while a limited app is open. Asking Android for
 * the whole stretch each time is heavy for the phone: the stretch starts before midnight, so
 * Android reads and parses yesterday's whole usage file for every ask (#106). Here the stretch is
 * read whole once, and then only what is new since the last read is asked for.
 *
 * [read] returns the events in [from, to), in order of time.
 */
internal class RecentEvents(private val read: (from: Long, to: Long) -> List<ScreenEvent>) {
    private var from = Long.MIN_VALUE
    private var until = Long.MIN_VALUE
    private var wholeReadAt = Long.MIN_VALUE
    private var events = ArrayList<ScreenEvent>()

    /**
     * The events in [[from], [to]), at the time [now]. Null for a stretch that isn't kept here (one
     * that ended a while ago, or a long one): the caller reads that from the log itself.
     */
    @Synchronized
    fun between(from: Long, to: Long, now: Long): List<ScreenEvent>? {
        // The clock was set back: what is kept may lie in what is now the future.
        if (now < until) forget()
        // Inside what is kept, such as the allowed hours within today: no read at all.
        if (from >= this.from && to <= until) return events.filter { it.time >= from && it.time < to }
        // Only a stretch that ends about now, and isn't much longer than a day, is kept.
        if (to < now - ENDS_NOW_MS || to - from > LONGEST_MS) return null
        if (from == this.from && now - wholeReadAt in 0 until WHOLE_EVERY_MS) {
            // The same stretch, a little later: only what is new. The log can gain an event a
            // moment after it happened, so the last few seconds are read again.
            val again = until - OVERLAP_MS
            while (events.isNotEmpty() && events.last().time >= again) events.removeAt(events.lastIndex)
            events.addAll(read(again, to))
        } else {
            // Another stretch (a new day), or time to read it whole again, in case the log gained
            // an older event since.
            events = ArrayList(read(from, to))
            this.from = from
            wholeReadAt = now
        }
        until = to
        return ArrayList(events)
    }

    private fun forget() {
        from = Long.MIN_VALUE
        until = Long.MIN_VALUE
        wholeReadAt = Long.MIN_VALUE
        events = ArrayList()
    }

    companion object {
        /** How much of the stretch's end is read again each time. */
        const val OVERLAP_MS = 10_000L

        /** How often the whole stretch is read again. */
        const val WHOLE_EVERY_MS = 10 * 60_000L

        /** A stretch "ends now" when it ends within this of now. */
        const val ENDS_NOW_MS = 60_000L

        /** The longest stretch that is kept: a day, with what is read before it, and room to spare. */
        const val LONGEST_MS = 30 * 60 * 60_000L
    }
}
