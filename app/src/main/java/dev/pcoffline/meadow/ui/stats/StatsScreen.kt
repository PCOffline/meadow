@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package dev.pcoffline.meadow.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.MonthHeatmap
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.StalkBarChart
import dev.pcoffline.meadow.ui.components.TabTitle
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.usage.UsageHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

enum class Period { DAY, WEEK, MONTH }

/** Which stretch of time is shown: [offset] periods back from the current one. */
data class Selection(val period: Period = Period.WEEK, val offset: Int = 0) {
    /** The first and last day of this stretch, counted from [today], with weeks starting on [firstDay]. */
    fun range(today: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> = when (period) {
        Period.DAY -> today.plusDays(offset.toLong()).let { it to it }
        Period.WEEK -> Days.weekStart(today, firstDay).plusWeeks(offset.toLong()).let { it to it.plusDays(6) }
        Period.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong()).let { it to it.plusMonths(1).minusDays(1) }
    }

    /** History older than [UsageHistory.KEEP_DAYS] is gone, so there's nothing earlier to show. */
    fun hasEarlier(today: LocalDate): Boolean = range(today).first.isAfter(today.minusDays(UsageHistory.KEEP_DAYS))

    companion object {
        /** [day] on its own; a day still to come shows today instead. */
        fun ofDay(day: LocalDate, today: LocalDate) = Selection(Period.DAY, ChronoUnit.DAYS.between(today, day).toInt().coerceAtMost(0))
    }
}

/** [canLimit] is false for the phone, settings and apps since uninstalled, which can't be limited. */
data class AppTime(val packageName: String, val label: String, val ms: Long, val limit: AppLimit?, val canLimit: Boolean = true)

/**
 * A stretch of usage. A day's time is null when the phone kept no record of it; [previousTotalMs]
 * is null when either stretch has such a day, since a comparison would be made up.
 */
data class PeriodStats(
    val selection: Selection,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<Pair<LocalDate, Long?>>,
    val apps: List<AppTime>,
    val totalMs: Long,
    val previousTotalMs: Long?,
    /** The day weeks start on, for laying out the month. */
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** Days up to today, so the days still to come don't pull the average down. */
    private fun counted(today: LocalDate) = days.filter { (day, ms) -> ms != null && !day.isAfter(today) }

    fun dailyAverageMs(today: LocalDate): Long = counted(today).let { d -> if (d.isEmpty()) 0L else d.sumOf { it.second ?: 0L } / d.size }

    /** Days so far that have no record. */
    fun missingDays(today: LocalDate): Int = days.count { (day, ms) -> ms == null && !day.isAfter(today) }
}

data class StatsUiState(
    val selection: Selection = Selection(),
    val stats: PeriodStats? = null,
    val hasPermission: Boolean = true,
    /** How things are going over the weeks, beside the current period. */
    val progress: Progress? = null,
)

class StatsViewModel : ViewModel() {
    private val selection = MutableStateFlow(Selection())

    private val loaded = combine(selection, AppGraph.store.weekRule) { sel, week -> sel to week.first }
        .mapLatest { (sel, firstDay) -> load(sel, firstDay) }
        .flowOn(Dispatchers.IO)

    // Read again when limits or the week's first day change, since both shape it.
    private val progress = combine(AppGraph.store.limits, AppGraph.store.weekRule) { _, _ -> }
        .mapLatest { AppGraph.progress.now() }
        .flowOn(Dispatchers.IO)
        .onStart<Progress?> { emit(null) }

    val state: StateFlow<StatsUiState> = combine(selection, loaded, AppGraph.store.limits, progress) { sel, stats, limits, progress ->
        val byPackage = limits.associateBy { it.packageName }
        StatsUiState(
            selection = sel,
            stats = stats.takeIf { it.selection == sel }?.let { s -> s.copy(apps = s.apps.map { it.copy(limit = byPackage[it.packageName]) }) },
            hasPermission = AppGraph.tracker.hasPermission(),
            progress = progress,
        )
    }.stateWhileVisible(this, StatsUiState())

    fun select(period: Period) {
        selection.value = Selection(period, 0)
    }

    /** Shows [day] on its own. */
    fun openDay(day: LocalDate) {
        selection.value = Selection.ofDay(day, Days.today())
    }

    fun shift(by: Int) {
        val current = selection.value
        if (by < 0 && !current.hasEarlier(Days.today())) return
        selection.value = current.copy(offset = (current.offset + by).coerceAtMost(0))
    }

    private fun load(sel: Selection, firstDay: DayOfWeek): PeriodStats {
        val today = Days.today()
        val (from, to) = sel.range(today, firstDay)
        val history = AppGraph.history
        val days = history.range(from, to)
        val limitable = AppGraph.catalog.launchablePackages()
        val perApp = HashMap<String, Long>()
        days.forEach { (_, apps) -> apps?.forEach { (pkg, ms) -> perApp[pkg] = (perApp[pkg] ?: 0L) + ms } }
        fun total(stretch: List<Pair<LocalDate, Map<String, Long>?>>): Long? =
            if (stretch.any { it.second == null }) null else stretch.sumOf { (_, apps) -> apps.orEmpty().values.sum() }
        val previousFrom = if (sel.period == Period.MONTH) from.minusMonths(1) else from.minusDays(ChronoUnit.DAYS.between(from, to) + 1)
        val previousTo = from.minusDays(1)
        val previous = if (sel.offset == 0) {
            // The current period is still running, so compare it with the same stretch of the last one.
            val fullDays = ChronoUnit.DAYS.between(from, today)
            val partial = previousFrom.plusDays(fullDays)
            if (partial.isAfter(previousTo)) {
                total(history.range(previousFrom, previousTo))
            } else {
                total(history.range(previousFrom, partial.minusDays(1)))
                    ?.plus(history.partialDay(partial, System.currentTimeMillis() - Days.startMillis(today)).values.sum())
            }
        } else {
            total(history.range(previousFrom, previousTo))
        }
        return PeriodStats(
            selection = sel,
            from = from,
            to = to,
            days = days.map { (d, apps) -> d to apps?.values?.sum() },
            apps = perApp.entries.sortedByDescending { it.value }.take(25)
                .map { AppTime(it.key, AppGraph.catalog.label(it.key), it.value, limit = null, canLimit = it.key in limitable) },
            totalMs = perApp.values.sum(),
            // Days with no record in this stretch would make any comparison look better than it is.
            previousTotalMs = previous.takeIf { days.none { (d, apps) -> apps == null && !d.isAfter(today) } },
            firstDay = firstDay,
        )
    }
}

@Composable
fun StatsScreen(onOpenSetup: () -> Unit, onOpenApp: (String) -> Unit, viewModel: StatsViewModel = viewModel { StatsViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(
        state,
        onSelect = viewModel::select,
        onShift = viewModel::shift,
        onOpenSetup = onOpenSetup,
        onOpenApp = onOpenApp,
        onOpenDay = viewModel::openDay,
    )
}

/** The statistics, drawn from plain state (also used for previews and screenshots). */
@Composable
fun StatsContent(
    state: StatsUiState,
    onSelect: (Period) -> Unit = {},
    onShift: (Int) -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.stats_title))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = state.selection.period == p,
                    onClick = { onSelect(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                ) {
                    Text(stringResource(when (p) { Period.DAY -> R.string.stats_day; Period.WEEK -> R.string.stats_week; Period.MONTH -> R.string.stats_month }))
                }
            }
        }
        if (!state.hasPermission) {
            EmptyState(
                "📊",
                stringResource(R.string.stats_no_access_title),
                action = { Button(onClick = onOpenSetup) { Text(stringResource(R.string.stats_no_access_action)) } },
            )
        } else {
            StatsBody(state, onShift, onOpenApp, onOpenDay)
        }
    }
}

@Composable
private fun StatsBody(state: StatsUiState, onShift: (Int) -> Unit, onOpenApp: (String) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PeriodNavigator(
            title = periodTitle(state.selection, state.stats),
            canGoBack = state.selection.hasEarlier(Days.today()),
            canGoForward = state.selection.offset < 0,
            onBack = { onShift(-1) },
            onForward = { onShift(1) },
        )
        AnimatedContent(
            targetState = state.selection,
            transitionSpec = {
                val forward = targetState.period.ordinal > initialState.period.ordinal ||
                    (targetState.period == initialState.period && targetState.offset > initialState.offset)
                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "period",
        ) { selection ->
            val stats = state.stats?.takeIf { it.selection == selection }
            if (stats == null) Loading() else PeriodContent(stats, state.progress.takeIf { selection.offset == 0 }, onOpenApp, onOpenDay)
        }
    }
}

@Composable
private fun periodTitle(selection: Selection, stats: PeriodStats?): String {
    if (stats == null) return "…"
    val locale = currentLocale()
    val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return when (selection.period) {
        Period.DAY -> when (selection.offset) {
            0 -> stringResource(R.string.stats_today)
            -1 -> stringResource(R.string.stats_yesterday)
            else -> stats.from.format(medium)
        }
        Period.WEEK -> if (selection.offset == 0) stringResource(R.string.stats_this_week)
        else stringResource(R.string.stats_range, stats.from.format(medium), stats.to.format(medium))
        // The locale decides the order and form, e.g. "October 2026" or "2026年10月".
        Period.MONTH -> stats.from.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy"), locale))
    }
}

@Composable
private fun PeriodContent(s: PeriodStats, progress: Progress?, onOpenApp: (String) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Summary(s) }
        // The summary already compares this period with the last.
        if (progress != null) item { ProgressCard(progress, Modifier.padding(horizontal = 20.dp), showWeekChange = false) }
        if (s.selection.period != Period.DAY) item { Chart(s, onOpenDay) }
        if (s.apps.isNotEmpty()) item { SectionTitle(stringResource(R.string.stats_where_time_went), Modifier.padding(horizontal = 20.dp)) }
        items(s.apps, key = { it.packageName }) { app -> AppRow(app, s.totalMs, { onOpenApp(app.packageName) }.takeIf { app.canLimit }, Modifier.animateItem()) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PeriodNavigator(title: String, canGoBack: Boolean, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.stats_earlier))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_later))
        }
    }
}

@Composable
private fun Summary(s: PeriodStats) {
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stats_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(durationText(s.totalMs), style = MaterialTheme.typography.headlineLarge)
            }
            if (s.selection.period != Period.DAY) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.stats_daily_average), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(durationText(s.dailyAverageMs(Days.today())), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        val previousTotal = s.previousTotalMs
        if (previousTotal != null && previousTotal > 0) {
            val change = ((s.totalMs - previousTotal) * 100 / previousTotal).toInt()
            val previous = stringResource(
                when (s.selection.period) {
                    Period.DAY -> if (s.selection.offset == 0) R.string.stats_so_far_day else R.string.stats_previous_day
                    Period.WEEK -> if (s.selection.offset == 0) R.string.stats_so_far_week else R.string.stats_previous_week
                    Period.MONTH -> if (s.selection.offset == 0) R.string.stats_so_far_month else R.string.stats_previous_month
                },
            )
            Spacer(Modifier.height(8.dp))
            WeekChangePill(change, previous)
        }
    }
}

@Composable
private fun Chart(s: PeriodStats, onOpenDay: (LocalDate) -> Unit) {
    val today = Days.today()
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        val minutes = s.days.map { (it.second ?: 0L) / 60_000f }
        val missing = s.days.indices.filter { s.days[it].second == null }.toSet()
        val highlight = s.days.indexOfFirst { it.first == today }
        if (s.selection.period == Period.WEEK) {
            val average = s.dailyAverageMs(today)
            StalkBarChart(
                values = minutes,
                labels = s.days.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale()) },
                highlightIndex = highlight,
                // The daily average above, drawn across the week and named where it ends.
                referenceLine = (average / 60_000f).takeIf { it > 0f },
                referenceLabel = stringResource(R.string.stats_average_tag).takeIf { average > 0 },
                missing = missing,
            )
        } else {
            val locale = currentLocale()
            val dayName = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEd"), locale)
            val noRecord = stringResource(R.string.stats_day_no_record)
            MonthHeatmap(
                values = minutes,
                labels = s.days.map { it.first.dayOfMonth.toString() },
                firstDay = s.firstDay,
                firstDayOffset = Math.floorMod(s.from.dayOfWeek.value - s.firstDay.value, 7),
                missing = missing,
                descriptions = s.days.map { (day, ms) -> "${day.format(dayName)}, ${ms?.let { durationText(it) } ?: noRecord}" },
                selectLabel = stringResource(R.string.stats_open_day),
                canSelect = { !s.days[it].first.isAfter(today) },
                onSelect = { onOpenDay(s.days[it].first) },
            )
            Spacer(Modifier.height(12.dp))
            StalkBarChart(
                values = minutes,
                labels = s.days.map { it.first.dayOfMonth.toString() },
                highlightIndex = highlight,
                missing = missing,
                height = 110.dp,
                showEveryNthLabel = 5,
            )
        }
        val unknown = s.missingDays(today)
        if (unknown > 0) {
            Text(
                pluralStringResource(R.plurals.stats_missing_days, unknown, unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** [ms] as a share (0 to 1) of [total]; nothing of nothing is 0. */
internal fun shareOf(ms: Long, total: Long): Float = if (total <= 0) 0f else (ms.toFloat() / total).coerceIn(0f, 1f)

@Composable
private fun AppRow(app: AppTime, total: Long, onOpen: (() -> Unit)?, modifier: Modifier) {
    // Seeing where the time went is the moment to act on it: a tap opens the app's limit.
    val action = stringResource(if (app.limit != null) R.string.stats_open_limit else R.string.stats_plant_limit)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClickLabel = action, onClick = onOpen) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (app.limit != null && app.limit.dailyMinutes > 0) {
                    Text(
                        stringResource(R.string.stats_limit_tag, minutesText(app.limit.dailyMinutes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(durationText(app.ms), style = MaterialTheme.typography.labelLarge)
            }
            // Every bar is the app's share of the screen time, the same number written under it.
            GrowthBar(shareOf(app.ms, total), height = 8.dp, color = MaterialTheme.colorScheme.primary)
            if (total > 0) {
                Text(
                    stringResource(R.string.stats_share, (shareOf(app.ms, total) * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
