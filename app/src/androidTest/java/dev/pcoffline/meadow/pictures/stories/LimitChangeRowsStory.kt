package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Test

/** A question to change a limit shows the whole limit, and what changes in it, wherever it shows. */
class LimitChangeRowsStory : Story("See what a limit change changes") {
    private enum class Where { CARD, CHAT, EDITOR, OLDER }

    private var where by mutableStateOf(Where.CARD)

    private val pkg = "demo.instagram"
    private val tenToFour = listOf(TimeWindow(10 * 60, 16 * 60))
    private val before = AppLimit(
        pkg, "Instagram", 30, 180,
        schedule = Schedule((1..7).associateWith { if (it == 7) emptyList() else tenToFour }),
        caps = listOf(TimeCap(listOf(7), 8 * 60, 12 * 60, 60)),
    )
    private val change = LimitRequest(
        id = "r1", requesterId = DemoGarden.ana.uid, requesterName = DemoGarden.ana.displayName, type = RequestType.CHANGE_LIMIT,
        packageName = pkg, appName = "Instagram", minutes = 0, dailyMinutes = 45, weeklyMinutes = 180,
        message = "Weekends are for long calls with family.", status = RequestStatus.PENDING, approverIds = listOf(DemoGarden.robin.uid),
        decidedByName = null, decisionNote = null, dayKey = "2026-11-27", createdAtMillis = System.currentTimeMillis() - 5 * 60_000,
        schedule = Schedule((1..7).associateWith { if (it == 6) listOf(TimeWindow(10 * 60, 20 * 60)) else if (it == 7) listOf(TimeWindow.ALL_DAY) else tenToFour }),
        caps = listOf(TimeCap(listOf(1, 2, 3, 4, 5), 18 * 60, 22 * 60, 30)),
        usedMinutes = 25, allowedMinutes = 30, previous = before,
    )

    // The times follow the phone's clock style, so match the start of what a screen reader says.
    private fun desc(start: String) = compose.onNode(hasContentDescription(start, substring = true))

    @Test
    fun story() {
        show(firstCaption = "Ana asks Robin, her Bumblebee, for a new Instagram limit. Each part that changes shows what it was.") {
            Box(Modifier.statusBarsPadding()) {
                when (where) {
                    Where.CARD, Where.OLDER -> Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        RequestCard(if (where == Where.OLDER) change.copy(previous = null) else change, DemoGarden.robin.uid)
                    }
                    Where.CHAT -> ChatContent(DemoGarden.ana, DemoGarden.chat, DemoGarden.messages, requestCard = { _, _ ->
                        RequestCard(change, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp), compact = true)
                    })
                    Where.EDITOR -> Column {
                        BackTopBar(compose.activity.getString(R.string.limit_editor_existing), onBack = {})
                        LimitEditor(pkg, LimitEditorState(existing = before, appName = "Instagram", bumblebeeCount = 1, pending = change, loaded = true), onDone = {})
                    }
                }
            }
        }
        step("Robin scrolls to the caps. A new cap and a removed one say so in words, not only in colour.") {
            desc("Removed cap:").performScrollTo()
        }
        step("Robin opens their chat with Ana. The question shows the same rows there.") {
            compose.runOnIdle { where = Where.CHAT }
        }
        step("Ana opens her Instagram limit. Her open question lists only what changes.") {
            compose.runOnIdle { where = Where.EDITOR }
            desc("Daily: from 30m").performScrollTo()
        }
        step("A question asked before this change shows the new limit alone.") {
            compose.runOnIdle { where = Where.OLDER }
        }
    }
}
