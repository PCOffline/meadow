package dev.pcoffline.meadow.enforcement

import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Grant
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker

/** Where one limited app stands right now. */
data class AppBudget(
    val limit: AppLimit,
    val usedTodayMs: Long,
    val usedWeekMs: Long,
    val bonusTodayMinutes: Int,
    val bonusWeekMinutes: Int,
) {
    val dailyAllowedMs: Long? =
        if (limit.dailyMinutes > 0) (limit.dailyMinutes + bonusTodayMinutes) * MINUTE else null
    val weeklyAllowedMs: Long? =
        if (limit.weeklyMinutes > 0) (limit.weeklyMinutes + bonusWeekMinutes) * MINUTE else null

    val dailyRemainingMs: Long? = dailyAllowedMs?.let { it - usedTodayMs }
    val weeklyRemainingMs: Long? = weeklyAllowedMs?.let { it - usedWeekMs }

    /** Time left before the tighter of the two limits is reached. */
    val remainingMs: Long = listOfNotNull(dailyRemainingMs, weeklyRemainingMs).minOrNull()?.coerceAtLeast(0) ?: Long.MAX_VALUE

    val exhausted: Boolean get() = remainingMs <= 0

    /** True when the weekly limit, not the daily one, is the one that runs out first. */
    val weeklyIsTighter: Boolean =
        weeklyRemainingMs != null && (dailyRemainingMs == null || weeklyRemainingMs < dailyRemainingMs)

    /** 0 = untouched, 1 = used up. Drives how bloomed each flower looks. */
    val usedFraction: Float
        get() {
            val daily = dailyAllowedMs?.let { usedTodayMs.toFloat() / it.coerceAtLeast(1) }
            val weekly = weeklyAllowedMs?.let { usedWeekMs.toFloat() / it.coerceAtLeast(1) }
            return (listOfNotNull(daily, weekly).maxOrNull() ?: 0f).coerceIn(0f, 1f)
        }

    companion object {
        const val MINUTE = 60_000L
    }
}

/**
 * Turns committed limits, approved grants and measured usage into budgets.
 * Usage is re-measured at most every [REFRESH_MS]; in between, the time the current
 * foreground app has been open is added on top so countdowns stay accurate.
 */
class LimitEngine(
    private val store: LocalStore,
    private val tracker: UsageTracker,
    private val history: UsageHistory,
) {
    private var todayKey: String = ""
    private var todayUsage: Map<String, Long> = emptyMap()
    private var measuredAt = 0L
    private var earlierThisWeek: Map<String, Long> = emptyMap()
    private var weekMeasuredFor = ""

    @Volatile private var foregroundPackage: String? = null
    @Volatile private var foregroundSince = 0L

    fun setForeground(packageName: String?) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        foregroundSince = System.currentTimeMillis()
    }

    fun limitFor(packageName: String): AppLimit? = store.limits.value.firstOrNull { it.packageName == packageName }

    fun budget(packageName: String, forceRefresh: Boolean = false): AppBudget? {
        val limit = limitFor(packageName) ?: return null
        return computeBudgets(listOf(limit), forceRefresh).firstOrNull()
    }

    fun budgets(forceRefresh: Boolean = false): List<AppBudget> =
        computeBudgets(store.limits.value, forceRefresh)

    /** Today's usage per app, measured fresh enough for display. */
    @Synchronized
    fun todayUsage(forceRefresh: Boolean = false): Map<String, Long> {
        refresh(forceRefresh)
        val extra = foregroundExtra()
        val pkg = foregroundPackage
        return if (pkg != null && extra > 0) todayUsage + (pkg to ((todayUsage[pkg] ?: 0L) + extra)) else todayUsage
    }

    @Synchronized
    private fun computeBudgets(limits: List<AppLimit>, forceRefresh: Boolean): List<AppBudget> {
        if (limits.isEmpty()) return emptyList()
        refresh(forceRefresh)
        val today = Days.today()
        val key = Days.key(today)
        val grants = store.grants.value
        val needsWeek = limits.any { it.weeklyMinutes > 0 }
        if (needsWeek && weekMeasuredFor != key) {
            val start = Days.weekStart(today)
            val totals = HashMap<String, Long>()
            if (start.isBefore(today)) {
                history.range(start, today.minusDays(1)).forEach { (_, day) ->
                    day.forEach { (pkg, ms) -> totals[pkg] = (totals[pkg] ?: 0L) + ms }
                }
            }
            earlierThisWeek = totals
            weekMeasuredFor = key
        }
        val extra = foregroundExtra()
        return limits.map { limit ->
            val pkg = limit.packageName
            val usedToday = (todayUsage[pkg] ?: 0L) + if (pkg == foregroundPackage) extra else 0L
            AppBudget(
                limit = limit,
                usedTodayMs = usedToday,
                usedWeekMs = (earlierThisWeek[pkg] ?: 0L) + usedToday,
                bonusTodayMinutes = grants.bonus(pkg) { it == key },
                bonusWeekMinutes = grants.bonus(pkg) { dayKey ->
                    Days.parse(dayKey)?.let { Days.isSameWeek(it, today) } == true
                },
            )
        }
    }

    private fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        val key = Days.todayKey()
        if (!force && key == todayKey && now - measuredAt < REFRESH_MS) return
        val today = Days.today()
        todayUsage = tracker.usageBetween(Days.startMillis(today), now)
        todayKey = key
        measuredAt = now
    }

    /** Time the current app has been open since usage was last measured. */
    private fun foregroundExtra(): Long {
        if (foregroundPackage == null || measuredAt == 0L) return 0L
        val from = maxOf(measuredAt, foregroundSince)
        return (System.currentTimeMillis() - from).coerceAtLeast(0L)
    }

    private inline fun List<Grant>.bonus(pkg: String, dayMatches: (String) -> Boolean): Int =
        filter { it.packageName == pkg && dayMatches(it.dayKey) }.sumOf { it.minutes }

    private companion object {
        const val REFRESH_MS = 20_000L
    }
}
