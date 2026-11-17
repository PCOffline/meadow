package dev.pcoffline.meadow.ui.screens

import android.app.AppOpsManager
import android.os.Process
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsScreen
import dev.pcoffline.meadow.ui.stats.StatsViewModel
import dev.pcoffline.meadow.usage.UsageHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Growth with the usage this phone kept: each period, moving back and forth, and a single day. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StatsScreenTest : ScreenTest() {
    private val today = Days.today()
    private lateinit var viewModel: StatsViewModel

    @Before
    fun keptUsage() {
        // An hour of a chat app on each of the last 60 days.
        val usage = (1..60).associate { back -> Days.key(today.minusDays(back.toLong())) to mapOf("com.example.chat" to 3_600_000L) }
        File(app.filesDir, UsageHistory.FILE).writeText(Json.encodeToString(usage))
        allowUsageAccess(true)
        viewModel = StatsViewModel(UsageHistory(app, AppGraph.tracker, AppGraph.catalog))
    }

    private fun allowUsageAccess(allowed: Boolean) {
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            app.packageName,
            if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED,
        )
    }

    private fun waitForText(value: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty() }

    private fun earlier() = compose.onNodeWithContentDescription(text(R.string.stats_earlier))
    private fun later() = compose.onNodeWithContentDescription(text(R.string.stats_later))

    private fun showStats(onOpenSetup: () -> Unit = {}) = show { StatsScreen(onOpenSetup = onOpenSetup, onOpenApp = {}, viewModel = viewModel) }

    @Test
    fun `this week shows the apps used, and earlier weeks are a tap away`() {
        showStats()
        waitForText(text(R.string.stats_this_week))
        // Without a record of today yet, a week that started today has no time in it.
        if (today != AppGraph.store.weekRule.value.start(today)) waitForText("Chat")
        later().assertIsNotEnabled()
        assertAccessible()

        earlier().performClick()
        val (from, to) = Selection(Period.WEEK, -1).range(today, AppGraph.store.weekRule.value.first)
        val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        waitForText(text(R.string.stats_range, from.format(medium), to.format(medium)))
        waitForText("Chat")
        later().assertIsEnabled().performClick()
        waitForText(text(R.string.stats_this_week))
    }

    @Test
    fun `a day is today, yesterday, then its date`() {
        showStats()
        compose.onNodeWithText(text(R.string.stats_day)).performClick()
        waitForText(text(R.string.stats_today))
        earlier().performClick()
        waitForText(text(R.string.stats_yesterday))
        waitForText("Chat")
        earlier().performClick()
        waitForText(today.minusDays(2).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())))
    }

    @Test
    fun `a month has its name, and a day of it opens on its own`() {
        showStats()
        waitForText(text(R.string.stats_this_week))
        compose.onNodeWithText(text(R.string.stats_month)).performClick()
        val month = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLLyyyy"), Locale.getDefault())
        waitForText(today.format(month))
        earlier().performClick()
        waitForText(today.minusMonths(1).format(month))
        waitForText("Chat")

        compose.runOnIdle { viewModel.openDay(today.minusDays(3)) }
        waitForText(today.minusDays(3).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())))
        // A day still to come shows today.
        compose.runOnIdle { viewModel.openDay(today.plusDays(3)) }
        waitForText(text(R.string.stats_today))
    }

    @Test
    fun `history ends where the phone stops keeping it`() {
        showStats()
        waitForText(text(R.string.stats_this_week))
        compose.onNodeWithText(text(R.string.stats_month)).performClick()
        compose.waitUntil(5_000) { viewModel.state.value.selection.period == Period.MONTH }
        // Months back to the oldest one kept; the earliest has nothing before it.
        repeat(10) { compose.runOnIdle { viewModel.shift(-1) } }
        val oldest = viewModel.state.value.selection
        assertEquals(false, oldest.hasEarlier(today))
        compose.runOnIdle { viewModel.shift(1) }
        compose.waitUntil(5_000) { viewModel.state.value.selection.offset == oldest.offset + 1 }
    }

    @Test
    fun `without usage access, Growth asks for it`() {
        allowUsageAccess(false)
        var setups = 0
        showStats(onOpenSetup = { setups++ })
        waitForText(text(R.string.stats_no_access_title))
        compose.onNodeWithText(text(R.string.stats_no_access_action)).performClick()
        assertEquals(1, setups)
    }
}
