@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.stats

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.MonthHeatmap
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.StalkBarChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Period(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

/** Usage over a period, with a total per day and per app. */
private data class PeriodStats(
    val title: String,
    val days: List<Pair<LocalDate, Long>>,
    val apps: List<Pair<String, Long>>,
    val totalMs: Long,
    val previousTotalMs: Long?,
)

@Composable
fun StatsScreen(nav: NavController) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var offset by rememberSaveable(period) { mutableIntStateOf(0) }
    val hasPermission = AppGraph.tracker.hasPermission()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Growth",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )
        Text(
            "How your time has been spent",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = { period = p },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                ) { Text(p.label) }
            }
        }
        if (!hasPermission) {
            EmptyState(
                "📊", "Usage access is off",
                "Meadow needs usage access to measure your screen time.",
                action = { Button(onClick = { nav.navigate("setup") }) { Text("Turn it on") } },
            )
        } else {
            PeriodContent(period, offset, onOffset = { offset = it })
        }
    }
}

@Composable
private fun PeriodContent(period: Period, offset: Int, onOffset: (Int) -> Unit) {
    val stats by produceState<PeriodStats?>(null, period, offset) {
        value = null
        value = withContext(Dispatchers.IO) { load(period, offset) }
    }
    Column(Modifier.fillMaxSize()) {
        PeriodNavigator(stats?.title ?: "…", canGoForward = offset < 0, onBack = { onOffset(offset - 1) }, onForward = { onOffset(offset + 1) })
        val s = stats
        if (s == null) Loading() else LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(s, period) }
            if (period != Period.DAY) item { Chart(s, period) }
            item { SectionTitle("Where the time went", Modifier.padding(horizontal = 20.dp)) }
            if (s.apps.isEmpty()) {
                item { EmptyState("🌾", "A quiet stretch", "No app time recorded for this period.") }
            }
            val max = s.apps.firstOrNull()?.second ?: 1L
            items(s.apps.take(25), key = { it.first }) { (pkg, ms) -> AppRow(pkg, ms, max, s.totalMs) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PeriodNavigator(title: String, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Earlier") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Later")
        }
    }
}

@Composable
private fun Summary(s: PeriodStats, period: Period) {
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(s.totalMs), style = MaterialTheme.typography.headlineLarge)
            }
            if (period != Period.DAY) {
                val counted = s.days.count { !it.first.isAfter(Days.today()) }.coerceAtLeast(1)
                Column(horizontalAlignment = Alignment.End) {
                    Text("Daily average", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(s.totalMs / counted), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        s.previousTotalMs?.takeIf { it > 0 }?.let { previous ->
            val change = ((s.totalMs - previous) * 100 / previous).toInt()
            val label = when (period) {
                Period.DAY -> "the day before"
                Period.WEEK -> "the week before"
                Period.MONTH -> "the month before"
            }
            Spacer(Modifier.height(8.dp))
            Pill(
                if (change <= 0) "🌱 ${-change}% less than $label" else "🍂 $change% more than $label",
                if (change <= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            )
        }
    }
}

@Composable
private fun Chart(s: PeriodStats, period: Period) {
    val today = Days.today()
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        val minutes = s.days.map { (it.second / 60_000f) }
        val highlight = s.days.indexOfFirst { it.first == today }
        if (period == Period.WEEK) {
            StalkBarChart(
                values = minutes,
                labels = s.days.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                highlightIndex = highlight,
                referenceLine = minutes.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            )
            Text(
                "Dashed line: your daily average",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            val first = s.days.first().first
            MonthHeatmap(
                values = minutes,
                labels = s.days.map { it.first.dayOfMonth.toString() },
                firstDayOffset = first.dayOfWeek.value - 1,
            )
            Spacer(Modifier.height(12.dp))
            StalkBarChart(
                values = minutes,
                labels = s.days.map { it.first.dayOfMonth.toString() },
                highlightIndex = highlight,
                height = 110.dp,
                showEveryNthLabel = 5,
            )
        }
    }
}

@Composable
private fun AppRow(pkg: String, ms: Long, max: Long, total: Long) {
    val limit = AppGraph.store.limits.value.firstOrNull { it.packageName == pkg }
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppGraph.catalog.label(pkg),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (limit != null && limit.dailyMinutes > 0) {
                    Text(
                        "limit ${formatMinutes(limit.dailyMinutes)}/day  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(formatDuration(ms), style = MaterialTheme.typography.labelLarge)
            }
            GrowthBar(ms.toFloat() / max.coerceAtLeast(1), height = 8.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
            if (total > 0) {
                Text(
                    "${(ms * 100 / total)}% of your screen time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun load(period: Period, offset: Int): PeriodStats {
    val today = Days.today()
    val history = AppGraph.history
    val (from, to, title) = when (period) {
        Period.DAY -> {
            val d = today.plusDays(offset.toLong())
            Triple(d, d, if (offset == 0) "Today" else if (offset == -1) "Yesterday" else d.format(DateTimeFormatter.ofPattern("EEE, MMM d")))
        }
        Period.WEEK -> {
            val start = Days.weekStart(today).plusWeeks(offset.toLong())
            val end = start.plusDays(6)
            Triple(start, end, if (offset == 0) "This week" else "${start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${end.format(DateTimeFormatter.ofPattern("MMM d"))}")
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
            Triple(start, start.plusMonths(1).minusDays(1), start.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
        }
    }
    val days = history.range(from, to)
    val perApp = HashMap<String, Long>()
    days.forEach { (_, apps) -> apps.forEach { (pkg, ms) -> perApp[pkg] = (perApp[pkg] ?: 0L) + ms } }
    val total = perApp.values.sum()

    val length = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
    val previous = if (period == Period.MONTH) {
        val prevStart = from.minusMonths(1)
        history.range(prevStart, from.minusDays(1))
    } else {
        history.range(from.minusDays(length), from.minusDays(1))
    }.sumOf { (_, apps) -> apps.values.sum() }

    return PeriodStats(
        title = title,
        days = days.map { (d, apps) -> d to apps.values.sum() },
        apps = perApp.entries.sortedByDescending { it.value }.map { it.key to it.value },
        totalMs = total,
        previousTotalMs = previous,
    )
}
