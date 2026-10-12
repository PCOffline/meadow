@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import java.time.DayOfWeek
import java.util.Locale
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.theme.Garden

/**
 * Rounded "stalk" bars. The highlighted bar (usually today) is drawn in petal pink;
 * an optional dashed line marks a limit or average.
 */
@Composable
fun StalkBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    referenceLine: Float? = null,
    height: Dp = 160.dp,
    showEveryNthLabel: Int = 1,
) {
    val garden = Garden.colors
    val outline = MaterialTheme.colorScheme.outline
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(700)) }
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(referenceLine ?: 0f).coerceAtLeast(1f)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val barHeight = (v / max) * size.height * progress.value
                val x = slot * i + (slot - barWidth) / 2f
                val color = if (i == highlightIndex) garden.petalPink else garden.leaf
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
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, label ->
                Text(
                    if (i % showEveryNthLabel == 0) label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlightIndex) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A soft progress bar; turns pink, then terracotta as it fills. */
@Composable
fun GrowthBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, color: Color? = null) {
    val garden = Garden.colors
    val f = fraction.coerceIn(0f, 1f)
    val fill = color ?: when {
        f >= 1f -> MaterialTheme.colorScheme.error
        f >= 0.8f -> garden.petalPink
        else -> garden.leaf
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

/** A month at a glance: one soft square per day, deeper green for more screen time. */
@Composable
fun MonthHeatmap(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, firstDayOffset: Int = 0) {
    val garden = Garden.colors
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val cells = List(firstDayOffset) { null } + values.indices.toList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (slot in 0 until 7) {
                    val index = week.getOrNull(slot)
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (index != null) {
                            val intensity = values[index] / max
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (values[index] <= 0f) MaterialTheme.colorScheme.surfaceVariant
                                        else garden.leafDark.copy(alpha = 0.18f + 0.72f * intensity),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    labels[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.55f) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
