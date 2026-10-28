package dev.pcoffline.meadow.enforcement

import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Grant
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.data.dailyMinutesOn
import dev.pcoffline.meadow.usage.AppCatalog
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where one limited app stands right now. */
data class AppBudget(
    val limit: AppLimit,
    val usedTodayMs: Long,
    val usedWeekMs: Long,
    val bonusTodayMinutes: Int,
    val bonusWeekMinutes: Int,
    /** An emergency unlock covering this app ends at this time; 0 when none is active. */
    val emergencyUntilMillis: Long = 0L,
    /** Outside the allowed hours right now. */
    val outsideHours: Boolean = false,
    /** Until the allowed hours end (or, outside them, start); null without a schedule. */
    val untilScheduleChangeMs: Long? = null,
    /** Extra time granted today that is still left for use outside the allowed hours. */
    val outsideHoursAllowanceMs: Long = 0L,
    /** Today's daily limit: the day's own, or the everyday one. 0 is none. */
    val dailyMinutesToday: Int = limit.dailyMinutes,
    /** Time left under the tightest cap whose hours are on now; null when none is. */
    val capRemainingMs: Long? = null,
    /** Until that cap's hours end. */
    val untilCapEndsMs: Long? = null,
    /** That cap. */
    val cap: TimeCap? = null,
) {
    val emergency: Boolean get() = emergencyUntilMillis > System.currentTimeMillis()

    val dailyAllowedMs: Long? =
        if (dailyMinutesToday > 0) (dailyMinutesToday + bonusTodayMinutes) * MINUTE else null
    val weeklyAllowedMs: Long? =
        if (limit.weeklyMinutes > 0) (limit.weeklyMinutes + bonusWeekMinutes) * MINUTE else null

    val dailyRemainingMs: Long? = dailyAllowedMs?.let { it - usedTodayMs }
    val weeklyRemainingMs: Long? = weeklyAllowedMs?.let { it - usedWeekMs }

    /** Time left before the tighter of the two limits is reached. */
    private val limitRemainingMs: Long =
        listOfNotNull(dailyRemainingMs, weeklyRemainingMs).minOrNull()?.coerceAtLeast(0) ?: Long.MAX_VALUE

    /** All the time today allows, spent or not; null when only the hours limit the app. */
    val allowedTodayMs: Long? = if (limitRemainingMs == Long.MAX_VALUE) null else usedTodayMs + limitRemainingMs

    /**
     * Time left before the app rests: the tighter limit, the end of the allowed hours, or a cap
     * within hours. Extra time granted today can also be spent outside the hours.
     */
    val remainingMs: Long = when {
        outsideHours -> minOf(limitRemainingMs, outsideHoursAllowanceMs.coerceAtLeast(0))
        untilScheduleChangeMs != null -> minOf(limitRemainingMs, untilScheduleChangeMs + outsideHoursAllowanceMs.coerceAtLeast(0))
        else -> limitRemainingMs
    }.let { if (capRemainingMs != null) minOf(it, capRemainingMs.coerceAtLeast(0)) else it }

    /** Resting because of the hours, not because the time ran out. */
    val restingByHours: Boolean get() = outsideHours && limitRemainingMs > 0 && remainingMs <= 0

    /** Resting until a cap's hours end, with time left in the day. */
    val restingByCap: Boolean
        get() = !restingByHours && capRemainingMs != null && capRemainingMs <= 0 && limitRemainingMs > 0 && !emergency

    /** Resting for now, to wake later today or another day: not because the time ran out. */
    val sleeping: Boolean get() = (restingByHours && !emergency) || restingByCap

    /** When a sleeping app wakes, from now; null when nothing changes within a week. */
    val wakesInMs: Long?
        get() = (if (restingByCap) untilCapEndsMs else untilScheduleChangeMs)?.takeIf { it < Long.MAX_VALUE / 4 }

    val exhausted: Boolean get() = remainingMs <= 0 && !emergency

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
    private val catalog: AppCatalog,
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

    /** Phone and emergency apps, launchers and the system UI are never limited, whatever is stored. */
    fun limitFor(packageName: String): AppLimit? {
        if (!limitable(packageName)) return null
        return store.limits.value.firstOrNull { it.packageName == packageName }
    }

    private fun limitable(packageName: String): Boolean =
        packageName !in catalog.essentialPackages() && packageName !in catalog.homePackages()

    fun budget(packageName: String, forceRefresh: Boolean = false): AppBudget? {
        val limit = limitFor(packageName) ?: return null
        return computeBudgets(listOf(limit), forceRefresh).firstOrNull()
    }

    fun budgets(forceRefresh: Boolean = false): List<AppBudget> =
        computeBudgets(store.limits.value.filter { limitable(it.packageName) }, forceRefresh)

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
        val week = store.weekRule.value
        if (needsWeek && weekMeasuredFor != key) {
            val start = week.limitStart(today)
            val totals = HashMap<String, Long>()
            if (start.isBefore(today)) {
                history.range(start, today.minusDays(1)).forEach { (_, day) ->
                    day?.forEach { (pkg, ms) -> totals[pkg] = (totals[pkg] ?: 0L) + ms }
                }
            }
            earlierThisWeek = totals
            weekMeasuredFor = key
        }
        val extra = foregroundExtra()
        val emergency = store.emergency.value
        val now = ZonedDateTime.now()
        val dayOfWeek = today.dayOfWeek.value
        val minuteNow = now.hour * 60 + now.minute
        return limits.map { limit ->
            val pkg = limit.packageName
            val usedToday = (todayUsage[pkg] ?: 0L) + if (pkg == foregroundPackage) extra else 0L
            val bonusToday = grants.bonus(pkg) { it == key }
            val schedule = limit.schedule
            val outside = schedule != null && !schedule.allows(now)
            AppBudget(
                limit = limit,
                usedTodayMs = usedToday,
                usedWeekMs = (earlierThisWeek[pkg] ?: 0L) + usedToday,
                bonusTodayMinutes = bonusToday,
                bonusWeekMinutes = grants.bonus(pkg) { dayKey ->
                    Days.parse(dayKey)?.let { week.limitStart(it) == week.limitStart(today) } == true
                },
                emergencyUntilMillis = emergency?.takeIf { it.covers(pkg) }?.untilMillis ?: 0L,
                outsideHours = outside,
                untilScheduleChangeMs = schedule?.let { it.untilChange(now)?.toMillis() ?: Long.MAX_VALUE / 2 },
                outsideHoursAllowanceMs = if (schedule == null || bonusToday == 0) 0L else {
                    val outsideUsed = usedToday - insideHoursToday(schedule)[pkg].orZero() -
                        if (pkg == foregroundPackage && !outside) extra else 0L
                    bonusToday * AppBudget.MINUTE - outsideUsed.coerceAtLeast(0L)
                },
                dailyMinutesToday = limit.dailyMinutesOn(dayOfWeek),
            ).withCaps(limit, dayOfWeek, minuteNow, now, bonusToday, if (pkg == foregroundPackage) extra else 0L)
        }
    }

    /** The tightest cap on now, with the day's extra time on top; all caps apply, so the tightest decides. */
    private fun AppBudget.withCaps(limit: AppLimit, dayOfWeek: Int, minuteNow: Int, now: ZonedDateTime, bonus: Int, extra: Long): AppBudget {
        val on = limit.caps.filter { dayOfWeek in it.days && it.window.contains(minuteNow) }
        if (on.isEmpty()) return this
        val pkg = limit.packageName
        val today = now.toLocalDate()
        val (cap, left) = on.map { cap ->
            val used = usageWithin(listOf(cap.window))[pkg].orZero() + extra
            cap to (cap.minutes + bonus) * AppBudget.MINUTE - used
        }.minBy { it.second }
        val ends = TimeWindow.edge(today, cap.to, now.zone).toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        return copy(capRemainingMs = left, untilCapEndsMs = ends.coerceAtLeast(0L), cap = cap)
    }

    private var insideHours: Map<List<TimeWindow>, Map<String, Long>> = emptyMap()
    private var insideHoursMeasuredAt = 0L

    /** Today's usage inside the allowed hours, measured with usage and cached alongside it. */
    private fun insideHoursToday(schedule: Schedule): Map<String, Long> =
        usageWithin(schedule.windowsOn(Days.today().dayOfWeek.value))

    /** Today's usage within [windows], measured with usage and cached alongside it. */
    private fun usageWithin(windows: List<TimeWindow>): Map<String, Long> {
        if (insideHoursMeasuredAt != measuredAt) {
            insideHours = emptyMap()
            insideHoursMeasuredAt = measuredAt
        }
        val today = Days.today()
        return insideHours[windows] ?: run {
            val zone = ZoneId.systemDefault()
            val totals = HashMap<String, Long>()
            for (w in windows) {
                val from = TimeWindow.edge(today, w.from, zone).toInstant().toEpochMilli()
                if (from >= measuredAt) continue
                val to = TimeWindow.edge(today, w.to, zone).toInstant().toEpochMilli()
                tracker.usageBetween(from, minOf(to, measuredAt)).orEmpty()
                    .forEach { (p, ms) -> totals[p] = totals[p].orZero() + ms }
            }
            totals.also { insideHours = insideHours + (windows to it) }
        }
    }

    private fun Long?.orZero() = this ?: 0L

    private fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        val key = Days.todayKey()
        if (!force && key == todayKey && now - measuredAt < REFRESH_MS) return
        val today = Days.today()
        todayUsage = tracker.usageBetween(Days.startMillis(today), now).orEmpty()
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
