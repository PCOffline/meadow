@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ControlPointDuplicate
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.capHoursText
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.daysText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val DAY = TimeWindow.MINUTES_IN_DAY

/** The most a day's limit can be, and the week's. */
private const val MAX_DAILY = DAY
private const val MAX_WEEKLY = 7 * DAY

/**
 * What the week card edits: the allowed hours ([schedule] null allows each hour), the caps, the
 * daily limit ([daily] 0 is off), the weekly limit ([weekly] 0 is off), and days with a daily
 * limit of their own ([dayMinutes]; 0 there is no limit that day).
 */
@Immutable
data class WeekLimit(
    val schedule: Schedule?,
    val caps: List<TimeCap>,
    val daily: Int,
    val weekly: Int,
    val dayMinutes: Map<Int, Int>,
)

/** Which amount the keypad is open for. */
private sealed interface Amount {
    data object Daily : Amount
    data object Weekly : Amount
    data class Day(val day: Int) : Amount
    data class CapAmount(val index: Int) : Amount
}

/** What Duplicate copies to other days. */
private sealed interface Copy {
    data class OfStretch(val stretch: Stretch) : Copy
    data class OfDay(val day: Int) : Copy
    data class OfCap(val index: Int) : Copy
}

/** What Fine-tune edits: [replacing] null adds new hours or a new cap. */
private data class Tuning(val start: FineTune, val replacing: Picked?)

/** The colours the grid draws in, from the theme. */
@Composable
internal fun weekColors(): WeekColors {
    val scheme = MaterialTheme.colorScheme
    return WeekColors(
        allowed = Garden.colors.growth.copy(alpha = 0.75f),
        restingA = scheme.surfaceVariant,
        restingB = scheme.outlineVariant.copy(alpha = 0.6f),
        cap = scheme.secondary,
        capDot = scheme.secondaryContainer.copy(alpha = 0.7f),
        onCap = scheme.onSecondary,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        line = scheme.outline,
        old = scheme.tertiary.copy(alpha = 0.35f),
        surface = Garden.colors.cardTint,
    )
}

/**
 * The week: the daily and weekly limits at the top, then a grid of the allowed hours and caps of
 * each day, starting on [firstDay]. Tapping a stretch, a cap or a day's name shows what can be
 * done to it. [step] is how far a drag moves; [onStep] changes it. Nothing changes while not
 * [enabled]; [waiting] says why, for a change that waits for a Bumblebee.
 */
@Composable
fun WeekCard(
    limit: WeekLimit,
    firstDay: DayOfWeek,
    step: Int,
    enabled: Boolean,
    onChange: (WeekLimit) -> Unit,
    onStep: (Int) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    waiting: Boolean = false,
    // What shows picked at first: the debug gallery shows the action bars with it.
    startPicked: Picked? = null,
) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val days = remember(firstDay) { weekFrom(firstDay) }
    val locale = currentLocale()
    var picked by remember { mutableStateOf(startPicked) }
    var redrawing by remember { mutableStateOf<Picked?>(null) }
    var amount by remember { mutableStateOf<Amount?>(null) }
    var copying by remember { mutableStateOf<Copy?>(null) }
    var tuning by remember { mutableStateOf<Tuning?>(null) }
    var steps by rememberSaveable { mutableStateOf(false) }
    // A day's stretches from before it was allowed all day or rested, to bring back with Hours.
    val earlierHours = remember { mutableStateMapOf<Int, List<TimeWindow>>() }
    var hoursShown by rememberSaveable(step) { mutableFloatStateOf(WeekPlan.hoursOnScreen(step).toFloat()) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val asList = config.screenWidthDp < 340 || config.fontScale >= 1.5f
    val tooMany = stringResource(R.string.week_too_many, MAX_WINDOWS)
    val allowedAll = stringResource(R.string.week_allow_all_done)
    val undo = stringResource(R.string.week_undo)

    fun say(message: String, onUndo: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = onUndo?.let { undo }, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }
    fun setSchedule(make: () -> Schedule?) {
        try {
            onChange(limit.copy(schedule = make()))
        } catch (_: TooManyStretches) {
            say(tooMany)
        }
    }
    fun dayName(day: Int, style: TextStyle = TextStyle.FULL) = DayOfWeek.of(day).getDisplayName(style, locale)
    fun stretchText(s: Stretch) = res.getString(
        R.string.week_stretch,
        dayName(s.day, TextStyle.SHORT), clockText(s.from),
        dayName(if (s.pastMidnight) nextDay(s.day) else s.day, TextStyle.SHORT), clockText(s.to % DAY),
    )
    fun allowAll() {
        val before = limit
        onChange(limit.copy(schedule = null))
        picked = null
        say(allowedAll) { onChange(before) }
    }
    fun setMode(day: Int, mode: DayMode) {
        val now = WeekPlan.modeOf(limit.schedule, day)
        if (now == DayMode.HOURS) earlierHours[day] = limit.schedule?.days?.get(day).orEmpty()
        setSchedule { WeekPlan.setMode(limit.schedule, day, mode, earlierHours[day].orEmpty()) }
    }
    fun pickAt(day: Int, minute: Int): Picked? {
        limit.caps.forEachIndexed { i, cap ->
            if (WeekPlan.capPartsOn(listOf(cap), day).any { (_, r) -> minute in r }) return Picked.Cap(i)
        }
        val stretches = WeekPlan.stretches(limit.schedule)
        stretches.firstOrNull { it.day == day && minute >= it.from && minute < minOf(it.to, DAY) }?.let { return Picked.Hours(it) }
        stretches.firstOrNull { it.pastMidnight && nextDay(it.day) == day && minute < it.to - DAY }?.let { return Picked.Hours(it) }
        return null
    }
    fun drawn(drawDays: List<Int>, from: Int, to: Int) {
        val redraw = redrawing
        redrawing = null
        when (redraw) {
            is Picked.Cap -> limit.caps.getOrNull(redraw.index)?.let { cap ->
                val made = runCatching { cap.copy(from = from, to = to) }.getOrNull()
                if (made == null) say(res.getString(R.string.limit_editor_cap_too_long)) else onChange(limit.copy(caps = limit.caps.replaceAt(redraw.index, made)))
            }
            is Picked.Hours -> setSchedule { WeekPlan.replace(limit.schedule, redraw.stretch, drawDays, from, to) }.also {
                picked = Picked.Hours(Stretch(drawDays.first(), from, to))
            }
            else -> setSchedule { WeekPlan.add(limit.schedule, drawDays, from, to) }.also {
                picked = Picked.Hours(Stretch(drawDays.first(), from, to))
            }
        }
    }
    fun resized(from: Int, to: Int) {
        when (val p = picked) {
            is Picked.Hours -> {
                setSchedule { WeekPlan.replace(limit.schedule, p.stretch, listOf(p.stretch.day), from, to) }
                picked = Picked.Hours(Stretch(p.stretch.day, from, to))
            }
            is Picked.Cap -> limit.caps.getOrNull(p.index)?.let { cap ->
                runCatching { cap.copy(from = from, to = to) }.getOrNull()?.let { onChange(limit.copy(caps = limit.caps.replaceAt(p.index, it))) }
                    ?: say(res.getString(R.string.limit_editor_cap_too_long))
            }
            else -> Unit
        }
    }
    fun dayDescription(day: Int): String {
        val mode = WeekPlan.modeOf(limit.schedule, day)
        val hours = when (mode) {
            DayMode.ALL_DAY -> res.getString(R.string.week_a11y_all_day)
            DayMode.RESTING -> res.getString(R.string.week_a11y_resting)
            DayMode.HOURS -> res.getString(
                R.string.week_a11y_hours,
                WeekPlan.stretchesOn(limit.schedule, day).joinToString(", ") { res.getString(R.string.schedule_window, clockText(it.from), clockText(it.to % DAY)) },
            )
        }
        val caps = limit.caps.filter { day in it.days }.map { res.getString(R.string.week_a11y_cap, res.minutesText(it.minutes), res.capHoursText(it)) }
        val daily = (limit.dayMinutes[day] ?: limit.daily).let {
            if (it > 0) res.getString(R.string.week_a11y_daily, res.minutesText(it)) else res.getString(R.string.week_a11y_no_daily)
        }
        val parts = (listOf(hours) + caps + daily).joinToString(". ")
        return res.getString(R.string.week_day_a11y, dayName(day), parts) +
            if (waiting) " " + res.getString(R.string.week_a11y_waiting) else ""
    }
    fun dayActions(day: Int): List<CustomAccessibilityAction> = if (!enabled) emptyList() else listOf(
        CustomAccessibilityAction(res.getString(R.string.week_add_hours)) { tuning = Tuning(FineTune(listOf(day), 600, 960), null); true },
        CustomAccessibilityAction(res.getString(R.string.week_add_cap)) { tuning = Tuning(FineTune(listOf(day), 600, 960, 30), null); true },
        CustomAccessibilityAction(res.getString(R.string.week_day_limit)) { amount = Amount.Day(day); true },
        CustomAccessibilityAction(res.getString(R.string.week_day_all)) { setMode(day, DayMode.ALL_DAY); true },
        CustomAccessibilityAction(res.getString(R.string.week_day_resting)) { setMode(day, DayMode.RESTING); true },
        CustomAccessibilityAction(res.getString(R.string.week_duplicate)) { copying = Copy.OfDay(day); true },
        CustomAccessibilityAction(res.getString(R.string.week_zoom_in)) { hoursShown = (hoursShown / 2).coerceAtLeast(3f); true },
        CustomAccessibilityAction(res.getString(R.string.week_zoom_out)) { hoursShown = (hoursShown * 2).coerceAtMost(24f); true },
    )

    // Zoomed in, the hours open at the first stretch.
    LaunchedEffect(hoursShown) {
        if (hoursShown >= 24f) return@LaunchedEffect
        val first = WeekPlan.stretches(limit.schedule).minOfOrNull { it.from } ?: (8 * 60)
        val hourPx = with(density) { (GRID_HEIGHT / hoursShown).toPx() }
        scroll.scrollTo(((first - 60).coerceAtLeast(0) / 60f * hourPx).toInt())
    }

    Column(modifier.fillMaxWidth()) {
        Header(step, enabled, canAllowAll = enabled && limit.schedule != null, onSteps = { steps = true }, onAllowAll = ::allowAll, onHelp = onHelp)
        Box {
            StepsMenu(steps, step, onDismiss = { steps = false }) {
                steps = false
                onStep(it)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountField(stringResource(R.string.limit_editor_daily), Icons.Outlined.Today, limit.daily.takeIf { it > 0 }, enabled, Modifier.weight(1f)) {
                amount = Amount.Daily
            }
            AmountField(stringResource(R.string.limit_editor_weekly), Icons.Outlined.DateRange, limit.weekly.takeIf { it > 0 }, enabled, Modifier.weight(1f)) {
                amount = Amount.Weekly
            }
        }
        if (redrawing != null) RedrawBar { redrawing = null }
        if (asList) {
            WeekList(limit, days, picked, enabled, ::dayDescription, ::dayActions) { day -> picked = if ((picked as? Picked.Day)?.day == day) null else Picked.Day(day) }
        } else {
            DayNames(days, limit, picked, enabled) { day -> picked = if ((picked as? Picked.Day)?.day == day) null else Picked.Day(day) }
            Box {
                WeekGrid(
                    schedule = limit.schedule,
                    caps = limit.caps,
                    days = days,
                    step = step,
                    hoursShown = hoursShown,
                    scroll = scroll,
                    colors = weekColors(),
                    picked = picked,
                    redrawing = redrawing,
                    enabled = enabled,
                    onZoom = { hoursShown = (hoursShown / it).coerceIn(3f, 24f) },
                    onTap = { day, minute -> if (redrawing == null) picked = pickAt(day, minute).takeUnless { it == picked } },
                    onDraw = ::drawn,
                    onResize = ::resized,
                    dayDescription = ::dayDescription,
                    dayActions = ::dayActions,
                )
                if (hoursShown < 24f || WeekPlan.hoursOnScreen(step) < 24) {
                    ZoomButtons(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        onIn = { hoursShown = (hoursShown / 2).coerceAtLeast(3f) },
                        onOut = { hoursShown = (hoursShown * 2).coerceAtMost(24f) },
                    )
                }
            }
        }
        if (limit.schedule == null && limit.caps.isEmpty() && enabled && !asList) Hint()
        Legend()
        when (val p = picked) {
            is Picked.Hours -> ActionBar(Icons.Outlined.Schedule, stretchText(p.stretch), onClose = { picked = null }) {
                Tool(Icons.Outlined.Gesture, R.string.week_redraw, enabled) { redrawing = p; picked = null }
                Tool(Icons.Outlined.Tune, R.string.week_fine_tune, enabled) { tuning = Tuning(FineTune(listOf(p.stretch.day), p.stretch.from, p.stretch.to), p) }
                Tool(Icons.Outlined.Timer, R.string.week_add_cap, enabled && limit.caps.size < TimeCap.MAX) {
                    tuning = Tuning(FineTune(listOf(p.stretch.day), p.stretch.from, p.stretch.to, minOf(30, p.stretch.to - p.stretch.from - 1)), null)
                }
                Tool(Icons.Outlined.ControlPointDuplicate, R.string.week_duplicate, enabled) { copying = Copy.OfStretch(p.stretch) }
                Tool(Icons.Outlined.Delete, R.string.week_remove, enabled, danger = true) {
                    val before = limit
                    val after = WeekPlan.remove(limit.schedule, p.stretch)
                    onChange(limit.copy(schedule = after))
                    picked = null
                    // The last stretch gone: each hour is allowed now, which Undo takes back.
                    if (after == null) say(allowedAll) { onChange(before) }
                }
            }
            is Picked.Cap -> limit.caps.getOrNull(p.index)?.let { cap ->
                ActionBar(
                    Icons.Outlined.Timer,
                    res.getString(R.string.limit_cap, res.minutesText(cap.minutes), res.capHoursText(cap), res.daysText(cap.days)).replaceFirstChar { it.uppercase() },
                    onClose = { picked = null },
                ) {
                    Tool(Icons.Outlined.Gesture, R.string.week_redraw, enabled) { redrawing = p; picked = null }
                    Tool(Icons.Outlined.Tune, R.string.week_fine_tune, enabled) { tuning = Tuning(FineTune(cap.days, cap.from, cap.to, cap.minutes), p) }
                    Tool(Icons.Outlined.HourglassTop, R.string.week_amount, enabled, sub = minutesText(cap.minutes)) { amount = Amount.CapAmount(p.index) }
                    Tool(Icons.Outlined.ControlPointDuplicate, R.string.week_duplicate, enabled) { copying = Copy.OfCap(p.index) }
                    Tool(Icons.Outlined.Delete, R.string.week_remove, enabled, danger = true) {
                        onChange(limit.copy(caps = limit.caps.filterIndexed { i, _ -> i != p.index }))
                        picked = null
                    }
                }
            }
            is Picked.Day -> ActionBar(Icons.Outlined.CalendarToday, dayName(p.day), onClose = { picked = null }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayModes(WeekPlan.modeOf(limit.schedule, p.day), enabled) { setMode(p.day, it) }
                    Row {
                        Tool(Icons.Outlined.MoreTime, R.string.week_add_hours, enabled) { tuning = Tuning(FineTune(listOf(p.day), 600, 960), null) }
                        Tool(Icons.Outlined.Timer, R.string.week_add_cap, enabled && limit.caps.size < TimeCap.MAX) {
                            tuning = Tuning(FineTune(listOf(p.day), 600, 960, 30), null)
                        }
                        val own = limit.dayMinutes[p.day]
                        Tool(
                            Icons.Outlined.Today, R.string.week_day_limit, enabled,
                            sub = when {
                                own == 0 -> stringResource(R.string.week_no_limit_day)
                                (own ?: limit.daily) > 0 -> minutesText(own ?: limit.daily)
                                else -> stringResource(R.string.week_off)
                            },
                        ) { amount = Amount.Day(p.day) }
                        Tool(Icons.Outlined.ControlPointDuplicate, R.string.week_duplicate, enabled) { copying = Copy.OfDay(p.day) }
                    }
                }
            }
            null -> Unit
        }
    }

    amount?.let { a ->
        val dismiss = { amount = null }
        when (a) {
            Amount.Daily -> DurationKeypadDialog(
                stringResource(R.string.limit_editor_daily), Icons.Outlined.Today, limit.daily.takeIf { it > 0 } ?: 30, MAX_DAILY,
                stringResource(R.string.keypad_up_to_day), listOf(15, 30, 45, 60, 90, 120), dismiss,
                choices = listOf(KeypadChoice(Icons.Outlined.TimerOff, stringResource(R.string.keypad_no_limit)) { onChange(limit.copy(daily = 0)); amount = null }),
            ) { onChange(limit.copy(daily = it)); amount = null }
            Amount.Weekly -> DurationKeypadDialog(
                stringResource(R.string.limit_editor_weekly), Icons.Outlined.DateRange, limit.weekly.takeIf { it > 0 } ?: 180, MAX_WEEKLY,
                stringResource(R.string.keypad_up_to_week), listOf(120, 180, 300, 420, 600), dismiss,
                choices = listOf(KeypadChoice(Icons.Outlined.TimerOff, stringResource(R.string.keypad_no_limit)) { onChange(limit.copy(weekly = 0)); amount = null }),
            ) { onChange(limit.copy(weekly = it)); amount = null }
            is Amount.Day -> DurationKeypadDialog(
                stringResource(R.string.keypad_day_title, dayName(a.day)), Icons.Outlined.CalendarToday,
                limit.dayMinutes[a.day]?.takeIf { it > 0 } ?: limit.daily.takeIf { it > 0 } ?: 60, MAX_DAILY,
                stringResource(R.string.keypad_up_to_day), listOf(15, 30, 45, 60, 90, 120), dismiss,
                choices = buildList {
                    if (limit.daily > 0) {
                        add(KeypadChoice(Icons.Outlined.RestartAlt, stringResource(R.string.keypad_reset, minutesText(limit.daily))) {
                            onChange(limit.copy(dayMinutes = limit.dayMinutes - a.day)); amount = null
                        })
                    }
                    add(KeypadChoice(Icons.Outlined.TimerOff, stringResource(R.string.keypad_no_limit)) {
                        onChange(limit.copy(dayMinutes = limit.dayMinutes + (a.day to 0))); amount = null
                    })
                },
            ) { onChange(limit.copy(dayMinutes = limit.dayMinutes + (a.day to it))); amount = null }
            is Amount.CapAmount -> limit.caps.getOrNull(a.index)?.let { cap ->
                DurationKeypadDialog(
                    stringResource(R.string.limit_editor_cap_minutes), Icons.Outlined.HourglassTop, cap.minutes, cap.to - cap.from - 1,
                    stringResource(R.string.limit_editor_cap_too_long), listOf(15, 30, 45, 60, 90, 120), dismiss,
                ) { onChange(limit.copy(caps = limit.caps.replaceAt(a.index, cap.copy(minutes = it)))); amount = null }
            }
        }
    }

    tuning?.let { t ->
        FineTuneSheet(t.start, firstDay, onDismiss = { tuning = null }) { result ->
            tuning = null
            if (result.minutes != null) {
                val cap = TimeCap(result.days, result.from, result.to, result.minutes)
                val replacing = t.replacing as? Picked.Cap
                onChange(limit.copy(caps = if (replacing != null) limit.caps.replaceAt(replacing.index, cap) else limit.caps + cap))
            } else {
                val replacing = (t.replacing as? Picked.Hours)?.stretch
                setSchedule {
                    if (replacing != null) WeekPlan.replace(limit.schedule, replacing, result.days, result.from, result.to)
                    else WeekPlan.add(limit.schedule, result.days, result.from, result.to)
                }
                picked = null
            }
        }
    }

    copying?.let { c ->
        val from = when (c) {
            is Copy.OfStretch -> listOf(c.stretch.day)
            is Copy.OfDay -> listOf(c.day)
            is Copy.OfCap -> limit.caps.getOrNull(c.index)?.days.orEmpty()
        }
        DuplicateDialog(from, firstDay, onDismiss = { copying = null }) { to ->
            copying = null
            when (c) {
                is Copy.OfStretch -> setSchedule { WeekPlan.duplicate(limit.schedule, c.stretch, to) }
                is Copy.OfDay -> {
                    val own = limit.dayMinutes[c.day]
                    val dayMinutes = if (own == null) limit.dayMinutes - to.toSet() else limit.dayMinutes + to.associateWith { own }
                    val caps = limit.caps.map { cap -> if (c.day in cap.days) cap.copy(days = (cap.days + to).distinct().sorted()) else cap }
                    try {
                        onChange(limit.copy(schedule = WeekPlan.duplicateDay(limit.schedule, c.day, to), caps = caps, dayMinutes = dayMinutes))
                    } catch (_: TooManyStretches) {
                        say(tooMany)
                    }
                }
                is Copy.OfCap -> limit.caps.getOrNull(c.index)?.let { cap ->
                    onChange(limit.copy(caps = limit.caps.replaceAt(c.index, cap.copy(days = (cap.days + to).distinct().sorted()))))
                }
            }
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }

/** "Your week", then the drag steps, Allow all and the guide. */
@Composable
private fun Header(step: Int, enabled: Boolean, canAllowAll: Boolean, onSteps: () -> Unit, onAllowAll: () -> Unit, onHelp: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarViewWeek, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            stringResource(R.string.week_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
        )
        val stepsLabel = stringResource(R.string.week_steps_now, minutesText(step))
        AssistChip(
            onClick = onSteps,
            enabled = enabled,
            label = { Text(minutesText(step)) },
            leadingIcon = { Icon(Icons.Outlined.Straighten, contentDescription = null, Modifier.size(18.dp)) },
            modifier = Modifier.semantics { contentDescription = stepsLabel },
        )
        IconButton(onClick = onAllowAll, enabled = canAllowAll) {
            Icon(Icons.Outlined.EventAvailable, contentDescription = stringResource(R.string.week_allow_all))
        }
        IconButton(onClick = onHelp) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.week_help))
        }
    }
}

/** The drag steps to choose from. */
@Composable
private fun StepsMenu(open: Boolean, step: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        Text(stringResource(R.string.week_steps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        DRAG_STEPS.forEach { s ->
            DropdownMenuItem(
                text = { Text(minutesText(s)) },
                onClick = { onPick(s) },
                trailingIcon = if (s == step) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                modifier = Modifier.semantics { selected = s == step },
            )
        }
    }
}

/** The names of the days over the grid, with a day's own daily limit under its name. */
@Composable
private fun DayNames(days: List<Int>, limit: WeekLimit, picked: Picked?, enabled: Boolean, onPick: (Int) -> Unit) {
    val locale = currentLocale()
    val scheme = MaterialTheme.colorScheme
    val today = java.time.LocalDate.now().dayOfWeek.value
    Row(
        Modifier.fillMaxWidth().padding(start = AXIS_WIDTH).pointerInput(days, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { pos ->
                val col = (pos.x / (size.width / days.size)).toInt().coerceIn(0, days.size - 1)
                onPick(days[col])
            }
        },
    ) {
        days.forEach { day ->
            val on = (picked as? Picked.Day)?.day == day
            Column(
                Modifier.weight(1f).padding(horizontal = 1.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) scheme.onSurface else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        on -> scheme.surface
                        day == today -> scheme.primary
                        else -> scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                val own = limit.dayMinutes[day]
                Text(
                    when (own) {
                        null -> ""
                        0 -> stringResource(R.string.week_no_limit_day)
                        else -> minutesText(own)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) scheme.surface else scheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * The week as a list, for large text and narrow phones: the hours, caps and own limit of each day.
 * A tap on a day shows its actions.
 */
@Composable
private fun WeekList(
    limit: WeekLimit,
    days: List<Int>,
    picked: Picked?,
    enabled: Boolean,
    describe: (Int) -> String,
    actions: (Int) -> List<CustomAccessibilityAction>,
    onPick: (Int) -> Unit,
) {
    val locale = currentLocale()
    val res = LocalResources.current
    Column {
        days.forEach { day ->
            val mode = WeekPlan.modeOf(limit.schedule, day)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = (picked as? Picked.Day)?.day == day, enabled = enabled, role = Role.Button) { onPick(day) }
                    .semantics {
                        contentDescription = describe(day)
                        customActions = actions(day)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    when (mode) {
                        DayMode.ALL_DAY -> Icons.Outlined.WbSunny
                        DayMode.HOURS -> Icons.Outlined.Schedule
                        DayMode.RESTING -> Icons.Outlined.Bedtime
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp, top = 2.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (mode) {
                            DayMode.ALL_DAY -> stringResource(R.string.week_day_all)
                            DayMode.RESTING -> stringResource(R.string.week_day_resting)
                            DayMode.HOURS -> WeekPlan.stretchesOn(limit.schedule, day).joinToString(", ") {
                                res.getString(R.string.schedule_window, clockText(it.from), clockText(it.to % DAY))
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    limit.caps.filter { day in it.days }.forEach {
                        Text(stringResource(R.string.limit_editor_cap_row, minutesText(it.minutes), res.capHoursText(it)), color = MaterialTheme.colorScheme.secondary)
                    }
                    limit.dayMinutes[day]?.let {
                        Text(if (it > 0) stringResource(R.string.limit_daily, minutesText(it)) else stringResource(R.string.week_a11y_no_daily), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** The dark bar over the grid while a stretch or cap is redrawn. */
@Composable
private fun RedrawBar(onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
            Text(
                stringResource(R.string.week_redraw_bar),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onStop) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.week_redraw_stop), tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

/** − and + for the hours, for one hand or no pinch. */
@Composable
private fun ZoomButtons(modifier: Modifier, onIn: () -> Unit, onOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp, color = Garden.colors.cardTint, modifier = modifier) {
        Row {
            IconButton(onClick = onOut) { Icon(Icons.Outlined.ZoomOut, contentDescription = stringResource(R.string.week_zoom_out)) }
            IconButton(onClick = onIn) { Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.week_zoom_in)) }
        }
    }
}

/** One small line under an empty grid: how to draw. */
@Composable
private fun Hint() {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.week_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** What the three patterns mean. */
@Composable
private fun Legend() {
    val colors = weekColors()
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.week_allowed to colors.allowed, R.string.week_resting to colors.restingB, R.string.week_cap to colors.cap).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** The bar under the grid for what is picked: its name, ✕, and its actions. */
@Composable
private fun ActionBar(icon: ImageVector, title: String, onClose: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).padding(start = 6.dp).semantics { heading() },
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.week_let_go)) }
            }
            Row(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** An action: an icon with a short name under it, and a value such as "30m" under that. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Tool(
    icon: ImageVector,
    label: Int,
    enabled: Boolean,
    danger: Boolean = false,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 6.dp),
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color)
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center, maxLines = 2)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/** Allowed all day, hours, or resting all day: the whole day with one tap. */
@Composable
private fun DayModes(mode: DayMode, enabled: Boolean, onPick: (DayMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, scheme.outline, RoundedCornerShape(12.dp))) {
        listOf(
            Triple(DayMode.ALL_DAY, Icons.Outlined.WbSunny, R.string.week_day_all),
            Triple(DayMode.HOURS, Icons.Outlined.Schedule, R.string.week_day_hours),
            Triple(DayMode.RESTING, Icons.Outlined.Bedtime, R.string.week_day_resting),
        ).forEach { (m, icon, label) ->
            val on = m == mode
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .background(if (on) Garden.colors.cardTint else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onPick(m) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (on) scheme.primary else scheme.onSecondaryContainer)
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (on) scheme.primary else scheme.onSecondaryContainer, textAlign = TextAlign.Center)
                if (on) Spacer(Modifier.height(3.dp).width(24.dp).background(scheme.primary))
            }
        }
    }
}

/** The days to duplicate to. The days it is on already stay picked. */
@Composable
private fun DuplicateDialog(from: List<Int>, firstDay: DayOfWeek, onDismiss: () -> Unit, onPick: (List<Int>) -> Unit) {
    var days by remember { mutableStateOf(from) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ControlPointDuplicate, contentDescription = null) },
        title = { Text(stringResource(R.string.week_duplicate_title)) },
        text = { DayChips(days, firstDay, enabled = true, taken = from) { days = (it + from).distinct().sorted() } },
        confirmButton = { TextButton(onClick = { onPick(days - from.toSet()) }, enabled = days.size > from.size) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** While waiting for an answer: the card shows the limit as it is now. */
@Composable
fun AsNowNote() {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.week_as_now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
