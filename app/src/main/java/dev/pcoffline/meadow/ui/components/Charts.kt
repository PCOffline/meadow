@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import java.time.DayOfWeek
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.pcoffline.meadow.ui.theme.contentOn
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.theme.Garden
import kotlin.math.ceil
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.R
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources

/**
 * Rounded "stalk" bars. The highlighted bar (usually today) is drawn in petal pink; an optional
 * dashed line marks a limit or average, named by a short [referenceLabel] just past its end. Days in [missing]
 * have no record and are drawn as empty outlines, so they don't read as days without use.
 */
@Composable
fun StalkBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    referenceLine: Float? = null,
    referenceLabel: String? = null,
    missing: Set<Int> = emptySet(),
    height: Dp = 160.dp,
    showEveryNthLabel: Int = 1,
) {
    val garden = Garden.colors
    val outline = MaterialTheme.colorScheme.outline
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val tagStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val tag = remember(referenceLabel, tagStyle) { referenceLabel?.let { measurer.measure(it, tagStyle) } }
    val density = LocalDensity.current
    // The line's name sits just past its end, in a narrow gutter where it can't cover a bar.
    val gutter = with(density) { tag?.let { it.size.width.toDp() + TAG_GAP } ?: 0.dp }
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(700)) }
    // The scale's top is a round length, named at the left, so the bars read as time (#81).
    val top = axisTopMinutes((values.maxOrNull() ?: 0f).coerceAtLeast(referenceLine ?: 0f))
    val max = top.toFloat()
    val topLabel = LocalResources.current.minutesText(top)
    val axisStyle = MaterialTheme.typography.labelSmall
    val axisPx = remember(topLabel, axisStyle) { maxOf(measurer.measure(topLabel, axisStyle).size.width, measurer.measure("0", axisStyle).size.width) }
    val axisWidth = with(density) { axisPx.toDp() + TAG_GAP }
    val described = stringResource(R.string.chart_axis_description, topLabel)

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(topLabel, style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text("0", style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = described }) {
            // A faint line at the top of the scale, where its label is.
            drawLine(outlineVariant, Offset(0f, 0f), Offset(size.width - gutter.toPx(), 0f), strokeWidth = 1.dp.toPx())
            val n = values.size.coerceAtLeast(1)
            val plot = size.width - gutter.toPx()
            val slot = plot / n
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val barHeight = (v / max) * size.height * progress.value
                val x = slot * i + (slot - barWidth) / 2f
                val color = if (i == highlightIndex) garden.growthHigh else garden.growth
                if (i in missing) {
                    drawRoundRect(
                        color = outlineVariant,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2f),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                    return@forEachIndexed
                }
                drawRoundRect(
                    color = color.copy(alpha = 0.18f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
            referenceLine?.let {
                val y = size.height - (it / max) * size.height
                drawLine(
                    color = outline,
                    start = Offset(0f, y),
                    end = Offset(plot, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
                tag?.let { t ->
                    val top = (y - t.size.height / 2f).coerceIn(0f, size.height - t.size.height)
                    drawText(t, topLeft = Offset(size.width - t.size.width, top))
                }
            }
        }
        }
        // Each label is centred under its bar but may be wider than it, so "15" isn't cut to "1".
        Layout(
            content = {
                labels.forEachIndexed { i, label ->
                    if (i % showEveryNthLabel == 0) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == highlightIndex) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = axisWidth, end = gutter),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val slot = width.toFloat() / labels.size.coerceAtLeast(1)
            val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                placeables.forEachIndexed { k, p ->
                    val center = slot * (k * showEveryNthLabel + 0.5f)
                    p.placeRelative((center - p.width / 2f).toInt().coerceIn(0, (width - p.width).coerceAtLeast(0)), 0)
                }
            }
        }
    }
}

/** A soft progress bar; turns pink, then terracotta as it fills. Every fill stands out from its track (3:1). */
@Composable
fun GrowthBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, color: Color? = null) {
    val garden = Garden.colors
    val f = fraction.coerceIn(0f, 1f)
    val fill = color ?: when {
        f >= 1f -> MaterialTheme.colorScheme.error
        f >= 0.8f -> garden.growthHigh
        else -> garden.growth
    }
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(fill),
        )
    }
}

/**
 * A month at a glance: one soft square per day, deeper green for more screen time; [missing] days
 * have no record. Weeks run from [firstDay]; the month's first day sits [firstDayOffset] squares in.
 *
 * With [onSelect], a tap on a day for which [canSelect] holds picks it; screen readers read each
 * day's [descriptions] and pick it with a double tap, named [selectLabel].
 */
@Composable
fun MonthHeatmap(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    firstDay: DayOfWeek = DayOfWeek.MONDAY,
    firstDayOffset: Int = 0,
    missing: Set<Int> = emptySet(),
    descriptions: List<String> = labels,
    selectLabel: String = "",
    canSelect: (Int) -> Boolean = { true },
    onSelect: ((Int) -> Unit)? = null,
) {
    val garden = Garden.colors
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val cells = List(firstDayOffset) { null } + values.indices.toList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0L until 7L) {
                Text(
                    firstDay.plus(i).getDisplayName(java.time.format.TextStyle.NARROW, currentLocale()), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (slot in 0 until 7) {
                    val index = week.getOrNull(slot)
                    // A square is narrower than 48dp, but a tap close enough still counts: Compose
                    // widens the touch area of anything clickable to the minimum.
                    val day = when {
                        index == null -> Modifier
                        onSelect != null && canSelect(index) -> Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = selectLabel, role = Role.Button) { onSelect(index) }
                            .clearAndSetSemantics { contentDescription = descriptions[index] }
                        else -> Modifier.clearAndSetSemantics { contentDescription = descriptions[index] }
                    }
                    Box(Modifier.weight(1f).height(40.dp).then(day), contentAlignment = Alignment.Center) {
                        if (index != null && index in missing) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(labels[index], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (index != null) {
                            val intensity = values[index] / max
                            val fill = if (values[index] <= 0f) MaterialTheme.colorScheme.surfaceVariant
                            else garden.leafDark.copy(alpha = 0.18f + 0.72f * intensity).compositeOver(MaterialTheme.colorScheme.surface)
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(fill),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Judged from the actual square, since the dark theme's green is light.
                                Text(labels[index], style = MaterialTheme.typography.labelSmall, color = contentOn(fill))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TAG_GAP = 6.dp

/**
 * The top of a time graph's scale, in minutes, for bars up to [maxMinutes]: a quarter hour under
 * an hour, whole hours up to six, and even hours above, so its label is a plain length.
 */
internal fun axisTopMinutes(maxMinutes: Float): Int {
    if (maxMinutes <= 0f) return 60
    if (maxMinutes <= 60f) return (ceil(maxMinutes / 15f).toInt() * 15).coerceAtLeast(15)
    val hours = ceil(maxMinutes / 60f).toInt()
    return (if (hours > 6) hours + hours % 2 else hours) * 60
}
