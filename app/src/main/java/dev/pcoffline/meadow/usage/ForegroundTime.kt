package dev.pcoffline.meadow.usage

/**
 * The parts of the system usage log that matter for screen time. [className] is the activity, so
 * one of an app's screens closing behind another of its screens doesn't end the visit.
 */
data class ScreenEvent(val time: Long, val kind: Kind, val packageName: String, val className: String? = null) {
    enum class Kind {
        /** An app's activity came to the front. */
        RESUMED,

        /** An app's activity went to the background. */
        PAUSED,

        /** The screen turned off, locked, or the phone shut down. */
        SCREEN_OFF,

        /** The phone booted. Whatever was open before it lost power ended at the last event before this. */
        STARTUP,
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
        var currentClass: String? = null
        var since = 0L
        var lastTime = Long.MIN_VALUE

        fun close(at: Long) {
            val pkg = current ?: return
            val from = maxOf(since, start)
            val to = minOf(at, end)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            current = null
            currentClass = null
        }

        for (event in events) {
            when (event.kind) {
                ScreenEvent.Kind.RESUMED -> if (current != event.packageName) {
                    close(event.time)
                    current = event.packageName
                    since = event.time
                }
                ScreenEvent.Kind.PAUSED -> if (closes(event, current, currentClass)) close(event.time)
                ScreenEvent.Kind.SCREEN_OFF -> close(event.time)
                ScreenEvent.Kind.STARTUP -> close(if (lastTime == Long.MIN_VALUE) event.time else lastTime)
            }
            if (event.kind == ScreenEvent.Kind.RESUMED) currentClass = event.className
            lastTime = event.time
        }
        close(end)
        return totals
    }

    /** What is on screen after replaying [events] on top of [current]. */
    fun foregroundAfter(current: String?, events: Iterable<ScreenEvent>): String? {
        var foreground = current
        var foregroundClass: String? = null
        for (event in events) {
            when (event.kind) {
                ScreenEvent.Kind.RESUMED -> {
                    foreground = event.packageName
                    foregroundClass = event.className
                }
                ScreenEvent.Kind.PAUSED -> if (closes(event, foreground, foregroundClass)) foreground = null
                ScreenEvent.Kind.SCREEN_OFF, ScreenEvent.Kind.STARTUP -> foreground = null
            }
        }
        return foreground
    }

    /**
     * A pause ends the visit only if it's the screen that was showing. Moving between an app's own
     * screens reports the old one's pause or stop after the new one has resumed.
     */
    private fun closes(event: ScreenEvent, current: String?, currentClass: String?): Boolean =
        current == event.packageName && (currentClass == null || event.className == null || event.className == currentClass)
}
