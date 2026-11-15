package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.LocalNow
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.opensAtText
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A time of day that a screen names counts from [LocalNow]. The pull request pictures pin it, so
 * the same screen is the same picture on every run.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PinnedClockTest : ScreenTest() {
    private val threeHours = 3 * 60 * 60_000L
    private val morning = ZonedDateTime.of(2026, 1, 5, 9, 41, 0, 0, ZoneId.systemDefault())
    private val lateEvening = morning.withHour(23).withMinute(30)

    // Outside its hours, and allowed again in three hours.
    private val night = DemoGarden.tiktok.copy(usedTodayMs = 0, outsideHours = true, untilScheduleChangeMs = threeHours)

    private fun rest(now: ZonedDateTime) = show {
        CompositionLocalProvider(LocalNow provides { now }) { RestContent("demo.tiktok", DemoGarden.rest.copy(budget = night)) }
    }

    @Test
    fun `the rest screen names the opening time from the pinned clock`() {
        rest(morning)
        compose.onNodeWithText(opensAtText(threeHours, morning), substring = true).assertExists()
    }

    @Test
    fun `an opening time after midnight has its day, from the pinned clock`() {
        rest(lateEvening)
        // Tuesday, at half past two.
        compose.onNodeWithText(opensAtText(threeHours, lateEvening), substring = true).assertExists()
    }

    @Test
    fun `a flower card names the opening time from the pinned clock`() {
        show {
            CompositionLocalProvider(LocalNow provides { lateEvening }) {
                GardenContent(DemoGarden.robin, GardenState(listOf(night), loaded = true, essentialsReady = true))
            }
        }
        compose.onNodeWithText(opensAtText(threeHours, lateEvening), substring = true).assertExists()
    }
}
