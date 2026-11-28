package dev.pcoffline.meadow.ui.limits

/**
 * An amount of time typed on a keypad, as in the Google Clock timer: digits fill hours and
 * minutes from the right. 5 is 5m, 50 is 50m, 500 is 5h. [fresh] holds the amount the dialog
 * opened with: the first key pressed starts a new amount.
 */
data class DurationEntry(val digits: String, val fresh: Boolean = false) {
    private val padded: String get() = digits.padStart(4, '0')

    /** The hours part, as typed: "01" in 01h 30m. */
    val hoursText: String get() = padded.take(2)

    /** The minutes part, as typed: "90" stays "90" until saved. */
    val minutesText: String get() = padded.takeLast(2)

    /** How many of the four places hold typed digits, from the right. */
    val typed: Int get() = digits.length

    /** The whole amount in minutes. Minutes over 59 count as more hours: 0h 90m is 90. */
    val minutes: Int get() = hoursText.toInt() * 60 + minutesText.toInt()

    /** [key] ("0" to "9", or "00") typed after the digits. Up to four digits; leading zeros drop. */
    fun type(key: String): DurationEntry {
        val start = if (fresh) "" else digits
        val next = (start + key).trimStart('0')
        return if (next.length > 4) copy(fresh = false) else DurationEntry(next)
    }

    /** The last digit taken off. */
    fun back(): DurationEntry = DurationEntry(if (fresh) "" else digits.dropLast(1))

    companion object {
        /** [minutes] as the keypad shows it: 90 is 01h 30m. */
        fun of(minutes: Int): DurationEntry =
            DurationEntry("%d%02d".format(minutes / 60, minutes % 60).trimStart('0'), fresh = true)
    }
}
