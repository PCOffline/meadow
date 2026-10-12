package dev.pcoffline.meadow.usage

/** The parts of the system usage log that matter for screen time. */
data class ScreenEvent(val time: Long, val kind: Kind, val packageName: String) {
    enum class Kind {
        /** An app's activity came to the front. */
        RESUMED,

        /** An app's activity went to the background. */
        PAUSED,

        /** The screen turned off, locked, or the phone shut down. */
        SCREEN_OFF,
    }
}

/**
 * Pure screen-time arithmetic over usage events. Only one app is treated as being on screen at a
 * time, which matches what the person actually sees.
 */
object ForegroundTime {

    /** Milliseconds each app spent on screen within [start, end). Events may begin before [start]. */
    fun totals(events: Iterable<ScreenEvent>, start: Long, end: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        var current: String? = null
        var since = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            val from = maxOf(since, start)
            val to = minOf(at, end)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            current = null
        }

        for (event in events) {
            when (event.kind) {
                ScreenEvent.Kind.RESUMED -> if (current != event.packageName) {
                    close(event.time)
                    current = event.packageName
                    since = event.time
                }
                ScreenEvent.Kind.PAUSED -> if (current == event.packageName) close(event.time)
                ScreenEvent.Kind.SCREEN_OFF -> close(event.time)
            }
        }
        close(end)
        return totals
    }

    /** What is on screen after replaying [events] on top of [current]. */
    fun foregroundAfter(current: String?, events: Iterable<ScreenEvent>): String? {
        var foreground = current
        for (event in events) {
            when (event.kind) {
                ScreenEvent.Kind.RESUMED -> foreground = event.packageName
                ScreenEvent.Kind.PAUSED -> if (foreground == event.packageName) foreground = null
                ScreenEvent.Kind.SCREEN_OFF -> foreground = null
            }
        }
        return foreground
    }
}
