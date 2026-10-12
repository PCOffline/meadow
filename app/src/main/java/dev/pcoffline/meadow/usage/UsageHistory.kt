package dev.pcoffline.meadow.usage

import android.content.Context
import dev.pcoffline.meadow.data.Days
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Daily usage per app. Finished days are cached on disk because the system event log only
 * reaches back a limited time, and monthly statistics need every day of the month.
 */
class UsageHistory(
    context: Context,
    private val tracker: UsageTracker,
    private val catalog: AppCatalog,
) {
    private val file = File(context.filesDir, "usage_history.json")
    private val cache: MutableMap<String, Map<String, Long>> = load()

    @Synchronized
    fun day(date: LocalDate): Map<String, Long> {
        val today = Days.today()
        val key = Days.key(date)
        if (date.isBefore(today)) cache[key]?.let { return it }
        if (date.isAfter(today)) return emptyMap()
        // A finished day is stored for good, so judge it against the apps installed now.
        if (date.isBefore(today)) catalog.invalidate()
        val raw = tracker.usageBetween(Days.startMillis(date), Days.endMillis(date))
        val usage = catalog.userFacing(raw)
        if (date.isBefore(today) && usage.isNotEmpty()) {
            cache[key] = usage
            prune(today)
            save()
        }
        return usage
    }

    fun range(from: LocalDate, to: LocalDate): List<Pair<LocalDate, Map<String, Long>>> {
        val days = mutableListOf<Pair<LocalDate, Map<String, Long>>>()
        var d = from
        while (!d.isAfter(to)) {
            days += d to day(d)
            d = d.plusDays(1)
        }
        return days
    }

    private fun prune(today: LocalDate) {
        val oldest = Days.key(today.minusDays(KEEP_DAYS))
        cache.keys.filter { it < oldest }.forEach { cache.remove(it) }
    }

    private fun load(): MutableMap<String, Map<String, Long>> =
        runCatching { Json.decodeFromString<Map<String, Map<String, Long>>>(file.readText()).toMutableMap() }
            .getOrDefault(mutableMapOf())

    private fun save() {
        runCatching { file.writeText(Json.encodeToString<Map<String, Map<String, Long>>>(cache)) }
    }

    private companion object {
        const val KEEP_DAYS = 120L
    }
}
