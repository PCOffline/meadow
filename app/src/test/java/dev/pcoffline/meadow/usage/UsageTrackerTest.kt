package dev.pcoffline.meadow.usage

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager.UsageStatsBuilder

/**
 * Days older than the event log come from Android's daily totals or not at all: its weekly and
 * monthly totals cover other days too, and once counted one day as a week of use.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class UsageTrackerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usm = shadowOf(context.getSystemService(UsageStatsManager::class.java))
    private val tracker = UsageTracker(context)
    private val hour = 60 * 60_000L

    private val old = Days.today().minusDays(20)
    private val start = Days.startMillis(old)
    private val end = Days.endMillis(old)

    private fun bucket(interval: Int, from: Long, to: Long, ms: Long) = usm.addUsageStats(
        interval,
        UsageStatsBuilder.newBuilder().setPackageName("com.example.feed").setFirstTimeStamp(from).setLastTimeStamp(to).setTotalTimeInForeground(ms).build(),
    )

    @Test
    fun `an old day with only weekly totals has no record`() {
        bucket(UsageStatsManager.INTERVAL_WEEKLY, start - 3 * 24 * hour, start + 4 * 24 * hour, 40 * hour)
        assertNull(tracker.usageBetween(start, end))
    }

    @Test
    fun `an old day uses its own daily total, not the week around it`() {
        bucket(UsageStatsManager.INTERVAL_WEEKLY, start - 3 * 24 * hour, start + 4 * 24 * hour, 40 * hour)
        bucket(UsageStatsManager.INTERVAL_DAILY, start - 24 * hour, start, 5 * hour)
        bucket(UsageStatsManager.INTERVAL_DAILY, start, end, 2 * hour)
        assertEquals(mapOf("com.example.feed" to 2 * hour), tracker.usageBetween(start, end))
    }

    @Test
    fun `an old day the event log still reaches is counted from the log`() {
        usm.addEvent("com.example.feed", start - hour, UsageEvents.Event.ACTIVITY_RESUMED)
        usm.addEvent("com.example.feed", start + hour, UsageEvents.Event.ACTIVITY_PAUSED)
        bucket(UsageStatsManager.INTERVAL_WEEKLY, start - 3 * 24 * hour, start + 4 * 24 * hour, 40 * hour)
        assertEquals(mapOf("com.example.feed" to hour), tracker.usageBetween(start, end))
    }

    @Test
    fun `a recent quiet day is known to be quiet`() {
        val recent = Days.today().minusDays(2)
        bucket(UsageStatsManager.INTERVAL_WEEKLY, Days.startMillis(recent) - 24 * hour, Days.endMillis(recent), 40 * hour)
        assertEquals(emptyMap<String, Long>(), tracker.usageBetween(Days.startMillis(recent), Days.endMillis(recent)))
    }
}
