package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.minutesText
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DAY = TimeWindow.MINUTES_IN_DAY

/** The width of the hours axis, left of the days. */
internal val AXIS_WIDTH = 36.dp

/** The gap between two days. */
private val DAY_GAP = 3.dp

/** How tall the whole day is, when the grid shows all of it. */
internal val GRID_HEIGHT = 216.dp

/** What the grid draws in, from the theme. */
@Immutable
data class WeekColors(
    val allowed: Color,
    val restingA: Color,
    val restingB: Color,
    val cap: Color,
    val capDot: Color,
    val onCap: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val old: Color,
    val surface: Color,
)

/** A stretch or cap picked on the grid, and the handles at its ends. */
sealed interface Picked {
    data class Hours(val stretch: Stretch) : Picked
    data class Cap(val index: Int) : Picked
    data class Day(val day: Int) : Picked
}

/** Something drawn now: a stretch from [from] to [to] (minutes from [firstDay]'s midnight) on [days]. */
private data class Drawing(val anchorDay: Int, val anchorMinute: Int, val day: Int, val minute: Int)

/**
 * The week's hours as a grid: a column for each of [days] (in the order the week starts), hours
 * from top to bottom. Green is allowed, stripes are resting, dots are a cap.
 *
 * - A long press, then a drag, draws hours on the days it crosses. [onDraw] gets them.
 * - With [redrawing], a plain drag draws, without the long press.
 * - A tap picks a stretch, a cap, or nothing ([onTap]).
 * - The ends of what is [picked] have handles: a drag moves that end ([onResize]).
 * - Two fingers zoom the hours ([zoom] is how many hours show at once).
 *
 * Each day is one node for screen readers, with [dayActions] and [dayDescription].
 */
@Composable
fun WeekGrid(
    schedule: Schedule?,
    caps: List<TimeCap>,
    days: List<Int>,
    step: Int,
    hoursShown: Float,
    scroll: ScrollState,
    colors: WeekColors,
    picked: Picked?,
    redrawing: Picked?,
    enabled: Boolean,
    onZoom: (Float) -> Unit,
    onTap: (day: Int, minute: Int) -> Unit,
    onDraw: (days: List<Int>, from: Int, to: Int) -> Unit,
    onResize: (from: Int, to: Int) -> Unit,
    dayDescription: (Int) -> String,
    dayActions: (Int) -> List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = GRID_HEIGHT,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val res = LocalResources.current
    val hourHeight = height / hoursShown
    val contentHeight = hourHeight * 24
    var drawing by remember { mutableStateOf<Drawing?>(null) }
    var resizing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val current by rememberUpdatedState(Triple(picked, redrawing, enabled))
    val draw by rememberUpdatedState(onDraw)
    val resize by rememberUpdatedState(onResize)
    val stepNow by rememberUpdatedState(step)
    // The gestures start once; they read the latest caps and callbacks through these.
    val capsNow by rememberUpdatedState(caps)
    val tap by rememberUpdatedState(onTap)
    val zoom by rememberUpdatedState(onZoom)

    Box(modifier.fillMaxWidth().height(minOf(height, contentHeight))) {
        Box(
            Modifier.fillMaxSize()
                // Two fingers zoom the hours; one finger is left to the other gestures.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val change = event.calculateZoom()
                                if (change != 1f) zoom(change)
                                event.changes.forEach(PointerInputChange::consume)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .verticalScroll(scroll, enabled = hoursShown < 24f),
        ) {
            Box(
                Modifier.fillMaxWidth().height(contentHeight)
                    .pointerInput(days, hoursShown) {
                        val hourPx = hourHeight.toPx()
                        val axisPx = AXIS_WIDTH.toPx()
                        val gapPx = DAY_GAP.toPx()
                        val colWidth = (size.width - axisPx - gapPx * (days.size - 1)) / days.size
                        fun dayAt(x: Float): Int = days[((x - axisPx) / (colWidth + gapPx)).toInt().coerceIn(0, days.size - 1)]
                        fun minuteAt(y: Float): Int = (y / hourPx * 60).roundToInt()
                        fun yOf(minute: Int): Float = minute / 60f * hourPx
                        fun xOf(day: Int): Float = axisPx + days.indexOf(day) * (colWidth + gapPx)
                        val edgePx = 24.dp.toPx()

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val (pick, redraw, on) = current
                            if (!on) return@awaitEachGesture
                            val ends = pick?.let { endsOf(it, capsNow) }
                            // A handle of what is picked: drag it to move that end.
                            val grabbed = ends?.let { (day, from, to) ->
                                val x = xOf(day) + colWidth / 2
                                val endX = xOf(if (to > DAY) nextDayIn(days, day) else day) + colWidth / 2
                                val endY = yOf(if (to > DAY) to - DAY else to)
                                when {
                                    abs(down.position.x - x) < colWidth && abs(down.position.y - yOf(from)) < edgePx -> true
                                    abs(down.position.x - endX) < colWidth && abs(down.position.y - endY) < edgePx -> false
                                    else -> null
                                }
                            }
                            if (ends != null && grabbed != null) {
                                val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                                var (from, to) = ends.second to ends.third
                                drag(slop.id) { change ->
                                    change.consume()
                                    val day = dayAt(change.position.x)
                                    val minute = WeekPlan.snap(minuteAt(change.position.y) + if (day != ends.first) DAY else 0, stepNow)
                                    if (grabbed) from = minute.coerceIn(0, to - stepNow) else to = minute.coerceIn(from + stepNow, from + DAY)
                                    resizing = from to to
                                }
                                resizing = null
                                resize(from, to)
                                return@awaitEachGesture
                            }
                            // Redrawing draws at once; otherwise a long press starts a drawing.
                            val start = if (redraw != null) {
                                awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            } else {
                                awaitLongPressOrCancellation(down.id)
                            } ?: return@awaitEachGesture
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val anchorDay = dayAt(down.position.x)
                            val anchorMinute = WeekPlan.snap(minuteAt(down.position.y), stepNow, DAY - stepNow)
                            drawing = Drawing(anchorDay, anchorMinute, anchorDay, anchorMinute + stepNow)
                            var lastStep = anchorMinute
                            drag(start.id) { change ->
                                change.consume()
                                val y = change.position.y
                                val minute = WeekPlan.snap(minuteAt(y), stepNow)
                                if (minute != lastStep) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastStep = minute
                                }
                                drawing = Drawing(anchorDay, anchorMinute, dayAt(change.position.x), minute)
                                // Near the top or bottom of what shows, the hours scroll on.
                                val inView = y - scroll.value
                                val viewHeight = height.toPx()
                                if (inView < edgePx) scope.launch { scroll.scrollBy(-hourPx / 4) }
                                if (inView > viewHeight - edgePx) scope.launch { scroll.scrollBy(hourPx / 4) }
                            }
                            drawing?.let { d ->
                                val (from, to) = span(d, stepNow)
                                draw(daysBetween(days, d.anchorDay, d.day), from, to)
                            }
                            drawing = null
                        }
                    }
                    .pointerInput(days, hoursShown) {
                        val hourPx = hourHeight.toPx()
                        val axisPx = AXIS_WIDTH.toPx()
                        val gapPx = DAY_GAP.toPx()
                        val colWidth = (size.width - axisPx - gapPx * (days.size - 1)) / days.size
                        detectTapGestures { pos ->
                            if (pos.x < axisPx) return@detectTapGestures
                            val day = days[((pos.x - axisPx) / (colWidth + gapPx)).toInt().coerceIn(0, days.size - 1)]
                            tap(day, (pos.y / hourPx * 60).toInt())
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val hourPx = hourHeight.toPx()
                    val axisPx = AXIS_WIDTH.toPx()
                    val gapPx = DAY_GAP.toPx()
                    val colWidth = (size.width - axisPx - gapPx * (days.size - 1)) / days.size
                    fun x(day: Int) = axisPx + days.indexOf(day) * (colWidth + gapPx)
                    fun y(minute: Int) = minute / 60f * hourPx
                    val radius = CornerRadius(5.dp.toPx())

                    // Hour lines and labels.
                    val every = if (hoursShown <= 12f) 1 else 6
                    for (h in 0..24 step every) {
                        val label = "%02d".format(h) + if (every == 1) ":00" else ""
                        val text = measurer.measure(label, TextStyle(fontSize = 10.sp, color = colors.muted))
                        val top = (y(h * 60) - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)
                        drawText(text, topLeft = Offset(axisPx - text.size.width - 4.dp.toPx(), top))
                    }

                    for (day in days) {
                        val left = x(day)
                        val mode = WeekPlan.modeOf(schedule, day)
                        if (mode == DayMode.ALL_DAY) {
                            drawRoundRect(colors.allowed, Offset(left, 0f), Size(colWidth, size.height), radius)
                        } else {
                            stripes(left, colWidth, size.height, colors.restingA, colors.restingB, radius)
                        }
                        // Lines at 6, 12 and 18 (each quarter hour when zoomed in).
                        val lineStep = if (hoursShown <= 6f) 15 else 360
                        var m = lineStep
                        while (m < DAY) {
                            drawLine(colors.line.copy(alpha = if (m % 60 == 0) 0.5f else 0.25f), Offset(left, y(m)), Offset(left + colWidth, y(m)), 1f)
                            m += lineStep
                        }
                    }

                    // Stretches, and the early hours of a stretch past midnight on the next day.
                    val stretches = WeekPlan.stretches(schedule)
                    for (s in stretches) {
                        if (s.day !in days) continue
                        val old = (redrawing as? Picked.Hours)?.stretch == s
                        val fill = if (old) colors.old else colors.allowed
                        val part = minOf(s.to, DAY)
                        drawPart(x(s.day), y(s.from), colWidth, y(part) - y(s.from), fill, radius, squareTop = false, squareBottom = s.pastMidnight)
                        if (s.pastMidnight) {
                            val next = nextDayIn(days, s.day)
                            drawPart(x(next), 0f, colWidth, y(s.to - DAY), fill, radius, squareTop = true, squareBottom = false)
                        }
                    }

                    // Caps, with their amount on the first part.
                    caps.forEachIndexed { i, cap ->
                        val old = (redrawing as? Picked.Cap)?.index == i
                        for (day in days) {
                            for ((c, range) in WeekPlan.capPartsOn(listOf(cap), day)) {
                                val top = y(range.first)
                                val height = y(range.last + 1) - top
                                val inset = 3.dp.toPx()
                                val squareTop = range.first == 0 && c.pastMidnight && day !in c.days
                                drawPart(x(day) + inset, top, colWidth - 2 * inset, height, if (old) colors.old else colors.cap, CornerRadius(4.dp.toPx()), squareTop, c.pastMidnight && !squareTop)
                                if (!old) dots(x(day) + inset, top, colWidth - 2 * inset, height, colors.capDot)
                                if (!squareTop && height > 14.dp.toPx()) {
                                    val text = measurer.measure(res.minutesText(c.minutes), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = colors.onCap))
                                    if (text.size.width < colWidth) drawText(text, topLeft = Offset(x(day) + (colWidth - text.size.width) / 2, top + 2.dp.toPx()))
                                }
                            }
                        }
                    }

                    // What is picked: a dark edge.
                    val edge = Stroke(2.dp.toPx())
                    when (picked) {
                        is Picked.Hours -> {
                            val s = picked.stretch
                            drawRoundRect(colors.ink, Offset(x(s.day), y(s.from)), Size(colWidth, y(minOf(s.to, DAY)) - y(s.from)), radius, style = edge)
                            if (s.pastMidnight) drawRoundRect(colors.ink, Offset(x(nextDayIn(days, s.day)), 0f), Size(colWidth, y(s.to - DAY)), radius, style = edge)
                        }
                        is Picked.Cap -> caps.getOrNull(picked.index)?.let { cap ->
                            for (day in days) for ((_, range) in WeekPlan.capPartsOn(listOf(cap), day)) {
                                drawRoundRect(colors.ink, Offset(x(day) + 3.dp.toPx(), y(range.first)), Size(colWidth - 6.dp.toPx(), y(range.last + 1) - y(range.first)), radius, style = edge)
                            }
                        }
                        is Picked.Day -> drawRoundRect(colors.ink, Offset(x(picked.day), 0f), Size(colWidth, size.height), radius, style = edge)
                        null -> Unit
                    }

                    // Handles at the ends of what is picked.
                    picked?.let { endsOf(it, caps) }?.let { (day, from, to) ->
                        handle(x(day) + colWidth / 2, y(resizing?.first ?: from), colors)
                        val end = resizing?.second ?: to
                        handle(x(if (end > DAY) nextDayIn(days, day) else day) + colWidth / 2, y(if (end > DAY) end - DAY else end), colors)
                    }

                    // The stretch being drawn, and its exact start and end on the axis.
                    drawing?.let { d ->
                        val (from, to) = span(d, step)
                        for (day in daysBetween(days, d.anchorDay, d.day)) {
                            drawPart(x(day), y(from), colWidth, y(minOf(to, DAY)) - y(from), colors.allowed.copy(alpha = 0.6f), radius, false, to > DAY)
                            drawRoundRect(colors.ink, Offset(x(day), y(from)), Size(colWidth, y(minOf(to, DAY)) - y(from)), radius, style = Stroke(2.dp.toPx()))
                            if (to > DAY) drawPart(x(nextDayIn(days, day)), 0f, colWidth, y(to - DAY), colors.allowed.copy(alpha = 0.6f), radius, true, false)
                        }
                        liveLabel(from, y(from), axisPx, size.width, measurer, colors)
                        liveLabel(to, y(if (to > DAY) to - DAY else to), axisPx, size.width, measurer, colors)
                    }
                    resizing?.let { (from, to) ->
                        liveLabel(from, y(from), axisPx, size.width, measurer, colors)
                        liveLabel(to, y(if (to > DAY) to - DAY else to), axisPx, size.width, measurer, colors)
                    }
                }
                // One node for each day, for screen readers, with the day's actions.
                Row(Modifier.fillMaxSize().padding(start = AXIS_WIDTH)) {
                    days.forEachIndexed { i, day ->
                        if (i > 0) Spacer(Modifier.width(DAY_GAP))
                        Box(
                            Modifier.weight(1f).fillMaxSize().clearAndSetSemantics {
                                contentDescription = dayDescription(day)
                                customActions = dayActions(day)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The day of a picked stretch or cap, and its start and end; null for a whole day. */
private fun endsOf(picked: Picked, caps: List<TimeCap>): Triple<Int, Int, Int>? = when (picked) {
    is Picked.Hours -> Triple(picked.stretch.day, picked.stretch.from, picked.stretch.to)
    is Picked.Cap -> caps.getOrNull(picked.index)?.let { Triple(it.days.first(), it.from, it.to) }
    is Picked.Day -> null
}

/** The start and end of what is drawn: up or down from where it started, a step at least, a day at most. */
private fun span(d: Drawing, step: Int): Pair<Int, Int> {
    val a = d.anchorMinute
    val b = d.minute
    val from = minOf(a, b).coerceIn(0, DAY - step)
    val to = maxOf(a, b, from + step).coerceAtMost(from + DAY)
    return from to to
}

/** The days from [a] to [b], in the order the grid shows them. */
private fun daysBetween(days: List<Int>, a: Int, b: Int): List<Int> {
    val i = days.indexOf(a)
    val j = days.indexOf(b)
    return days.subList(minOf(i, j), maxOf(i, j) + 1)
}

/** The day after [day], round the week. */
private fun nextDayIn(days: List<Int>, day: Int): Int = days[(days.indexOf(day) + 1) % days.size]

private fun DrawScope.drawPart(left: Float, top: Float, width: Float, height: Float, color: Color, radius: CornerRadius, squareTop: Boolean, squareBottom: Boolean) {
    if (height <= 0f) return
    drawRoundRect(color, Offset(left, top), Size(width, height), radius)
    if (squareTop) drawRect(color, Offset(left, top), Size(width, minOf(radius.y, height)))
    if (squareBottom) drawRect(color, Offset(left, top + height - minOf(radius.y, height)), Size(width, minOf(radius.y, height)))
}

/** Resting: diagonal stripes. */
private fun DrawScope.stripes(left: Float, width: Float, height: Float, a: Color, b: Color, radius: CornerRadius) {
    drawRoundRect(a, Offset(left, 0f), Size(width, height), radius)
    val gap = 8.dp.toPx()
    clipRect(left, 0f, left + width, height) {
        var x = left - height
        while (x < left + width) {
            drawLine(b, Offset(x, height), Offset(x + height, 0f), 3.dp.toPx())
            x += gap
        }
    }
}

/** A cap: small dots, so it reads as a cap without its colour. */
private fun DrawScope.dots(left: Float, top: Float, width: Float, height: Float, color: Color) {
    val gap = 6.dp.toPx()
    clipRect(left, top, left + width, top + height) {
        var y = top + gap / 2
        while (y < top + height) {
            var x = left + gap / 2
            while (x < left + width) {
                drawCircle(color, 1.2.dp.toPx(), Offset(x, y))
                x += gap
            }
            y += gap
        }
    }
}

private fun DrawScope.handle(cx: Float, cy: Float, colors: WeekColors) {
    val w = 22.dp.toPx()
    val h = 8.dp.toPx()
    drawRoundRect(colors.surface, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(h / 2))
    drawRoundRect(colors.ink, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(h / 2), style = Stroke(2.dp.toPx()))
}

/** A dark label on the axis with the exact time, and a thin line across the grid. */
private fun DrawScope.liveLabel(minute: Int, y: Float, axisPx: Float, width: Float, measurer: androidx.compose.ui.text.TextMeasurer, colors: WeekColors) {
    drawLine(colors.ink, Offset(axisPx, y), Offset(width, y), 1.5.dp.toPx())
    val text = measurer.measure(dev.pcoffline.meadow.ui.clockText(minute % DAY), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = colors.surface))
    val pad = 3.dp.toPx()
    val w = text.size.width + 2 * pad
    val h = text.size.height.toFloat()
    val top = (y - h / 2).coerceIn(0f, size.height - h)
    drawRoundRect(colors.ink, Offset(axisPx - w - 1.dp.toPx(), top), Size(w, h), CornerRadius(5.dp.toPx()))
    drawText(text, topLeft = Offset(axisPx - w - 1.dp.toPx() + pad, top))
}
