package dev.pcoffline.meadow.ui.limits

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import java.time.DayOfWeek

/** The steps of the guide: a title, a text and what the sample shows. */
private val STEPS = listOf(
    R.string.guide_draw_title to R.string.guide_draw_text,
    R.string.guide_stretch_title to R.string.guide_stretch_text,
    R.string.guide_day_title to R.string.guide_day_text,
    R.string.guide_tools_title to R.string.guide_tools_text,
    R.string.guide_time_title to R.string.guide_time_text,
)

/** A sample week: 10:00–22:00 on weekdays, 06:00–22:00 at weekends, and a cap at weekends. */
private val SAMPLE = Schedule((1..7).associateWith { if (it >= 6) listOf(TimeWindow(360, 1320)) else listOf(TimeWindow(600, 1320)) })
private val SAMPLE_CAPS = listOf(TimeCap(listOf(6, 7), 840, 1320, 30))

/**
 * How to set the week, in five steps, over a sample week: draw, tap a stretch, tap a day, the drag
 * steps and Allow all, and the amounts of time. It changes no limit. [onDone] closes it: Skip, Got
 * it, Back on the first step, or a tap outside.
 */
@Composable
fun WeekGuide(appName: String, firstDay: DayOfWeek, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Scene(step, firstDay)
                val (title, text) = STEPS[step]
                Text(
                    stringResource(title, appName),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                val position = stringResource(R.string.guide_step, step + 1, STEPS.size)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (step == 0) onDone() else step-- }) {
                        Text(stringResource(if (step == 0) R.string.guide_skip else R.string.action_back))
                    }
                    Row(
                        Modifier.weight(1f).clearAndSetSemantics { contentDescription = position },
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        STEPS.indices.forEach { i ->
                            val on = i == step
                            Surface(
                                shape = CircleShape,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(if (on) 20.dp else 8.dp, 8.dp),
                            ) {}
                        }
                    }
                    Button(onClick = { if (step == STEPS.lastIndex) onDone() else step++ }) {
                        Text(stringResource(if (step == STEPS.lastIndex) R.string.guide_done else R.string.guide_next))
                    }
                }
            }
        }
    }
}

/** What each step shows, on the sample week. */
@Composable
private fun Scene(step: Int, firstDay: DayOfWeek) {
    val days = remember(firstDay) { weekFrom(firstDay) }
    val ring = Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(4.dp)
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        when (step) {
            0 -> {
                // A hand draws Mon to Fri, 10:00 to 18:00, again and again; still with reduced motion.
                val context = LocalContext.current
                val still = remember { animationsOff(context) }
                val end = if (still) {
                    1080f
                } else {
                    val t = rememberInfiniteTransition(label = "draw")
                    t.animateFloat(660f, 1080f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "end").value
                }
                val schedule = WeekPlan.add(Schedule((1..5).associateWith { emptyList() }), (1..5).toList(), 600, (end / 10).toInt() * 10)
                SampleGrid(schedule, emptyList(), days, null)
            }
            1 -> SampleGrid(SAMPLE, SAMPLE_CAPS, days, Picked.Hours(Stretch(3, 600, 1320)))
            2 -> SampleGrid(SAMPLE, SAMPLE_CAPS, days, Picked.Day(6))
            3 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(ring) {
                    AssistChip(onClick = {}, label = { Text("10m") }, leadingIcon = { Icon(Icons.Outlined.Straighten, contentDescription = null) })
                }
                Box(ring) { Icon(Icons.Outlined.EventAvailable, contentDescription = null, Modifier.padding(8.dp)) }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(ring.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField(stringResource(R.string.limit_editor_daily), Icons.Outlined.Today, 30, enabled = false, modifier = Modifier.weight(1f)) {}
                    AmountField(stringResource(R.string.limit_editor_weekly), Icons.Outlined.DateRange, 180, enabled = false, modifier = Modifier.weight(1f)) {}
                }
                Box(ring) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, Modifier.padding(8.dp)) }
            }
        }
    }
}

/** The sample week as the grid draws it, read only. */
@Composable
private fun SampleGrid(schedule: Schedule?, caps: List<TimeCap>, days: List<Int>, picked: Picked?) {
    WeekGrid(
        schedule = schedule,
        caps = caps,
        days = days,
        step = DEFAULT_DRAG_STEP,
        hoursShown = 24f,
        scroll = rememberScrollState(),
        colors = weekColors(),
        picked = picked,
        redrawing = null,
        enabled = false,
        onZoom = {},
        onTap = { _, _ -> },
        onDraw = { _, _, _ -> },
        onResize = { _, _ -> },
        dayDescription = { "" },
        dayActions = { emptyList() },
    )
}

/** Whether the phone's animations are off (reduced motion). */
private fun animationsOff(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
