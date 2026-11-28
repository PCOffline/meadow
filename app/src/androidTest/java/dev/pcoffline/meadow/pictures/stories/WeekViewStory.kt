package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import org.junit.Test
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The week card (#216): draw hours, pick and redraw them, set a whole day and its own limit, and the guide. */
class WeekViewStory : Story("Set hours, caps and limits on one week") {
    private val pkg = "demo.youtube"

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun full(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())
    private fun short(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** [day]'s column in the grid. */
    private fun column(day: Int) = compose.onNode(hasContentDescription("${full(day)}.", substring = true))

    /** A long press at [from] o'clock on [first], then a drag to [to] o'clock on [last]. The whole day shows. */
    private fun draw(first: Int, last: Int, from: Float, to: Float) {
        val start = column(first).performScrollTo().fetchSemanticsNode().boundsInRoot
        val end = column(last).fetchSemanticsNode().boundsInRoot
        column(first).performTouchInput {
            val x = center.x
            down(Offset(x, height * from / 24f))
            advanceEventTime(800)
            moveTo(Offset(x + (end.center.x - start.center.x), height * (from + to) / 48f))
            moveTo(Offset(x + (end.center.x - start.center.x), height * to / 24f))
            up()
        }
    }

    @Test
    fun story() {
        // The whole day shows at once with 15-minute steps. Put back at the end of the story.
        AppGraph.store.setDragStep(pkg, 15)
        AppGraph.store.setWeekGuideSeen()
        try {
            show(firstCaption = "A new limit: 30m a day. Each hour is allowed until hours are drawn.") {
                Column(Modifier.statusBarsPadding()) {
                    BackTopBar(text(R.string.limit_editor_new), onBack = {})
                    LimitEditor(pkg, LimitEditorState(appName = "YouTube", loaded = true), onDone = {})
                }
            }
            step("Press and hold Monday at 10:00, then drag to 18:00 on Friday.") { draw(1, 5, 10f, 18f) }
            step("Tap Monday's hours: handles at each end, and what can be done to them.") {
                column(1).performTouchInput { click(Offset(center.x, height * 14f / 24f)) }
            }
            step("Redraw: the old hours fade, and the bar says what to do.") {
                compose.onNodeWithText(text(R.string.week_redraw)).performScrollTo().performClick()
            }
            step("A drag from 09:00 to 17:00 puts the new hours in their place.") { draw(1, 1, 9f, 17f) }
            step("Tap Saturday's name for the whole day.") {
                compose.onNode(hasText(short(6))).performTouchInput { click() }
            }
            step("Rest Saturday all day with one tap.") {
                compose.onNodeWithText(text(R.string.week_day_resting)).performScrollTo().performClick()
            }
            step("Day limit opens the keypad, with quick picks.") {
                compose.onNodeWithText(text(R.string.week_day_limit)).performScrollTo().performClick()
            }
            step("Type 1, 0, 0: one hour.") {
                listOf("1", "0", "0").forEach { compose.onNode(hasText(it)).performClick() }
            }
            step("OK: Saturday shows 1h under its name.") {
                compose.onNodeWithText(text(R.string.action_ok)).performClick()
            }
            step("The daily limit is a field: tap it to type another time.") {
                compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} 30m").performScrollTo().performClick()
            }
            step("Pick 45m, then OK.") {
                compose.onNode(hasText("45m")).performClick()
                compose.onNodeWithText(text(R.string.action_ok)).performClick()
            }
            step("The question mark opens the guide, a step at a time.") {
                compose.onNodeWithContentDescription(text(R.string.week_help)).performScrollTo().performClick()
            }
            step("Next: tap a stretch to redraw, fine-tune, cap, duplicate or remove it.") {
                compose.onNodeWithText(text(R.string.guide_next)).performClick()
            }
        } finally {
            AppGraph.store.setDragStep(pkg, 10)
        }
    }
}
