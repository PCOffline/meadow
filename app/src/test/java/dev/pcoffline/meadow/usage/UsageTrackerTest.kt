package dev.pcoffline.meadow.usage

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `an app on screen since long before the stretch is counted from its start`() {
        // #159: opened in the evening and still on screen an hour after midnight.
        val stretchStart = System.currentTimeMillis() - hour
        usm.addEvent("com.example.feed", stretchStart - 4 * hour, UsageEvents.Event.ACTIVITY_RESUMED)
        assertEquals(mapOf("com.example.feed" to hour), tracker.usageBetween(stretchStart, stretchStart + hour))
    }

    @Test
    fun `a recent quiet day is known to be quiet`() {
        val recent = Days.today().minusDays(2)
        bucket(UsageStatsManager.INTERVAL_WEEKLY, Days.startMillis(recent) - 24 * hour, Days.endMillis(recent), 40 * hour)
        assertEquals(emptyMap<String, Long>(), tracker.usageBetween(Days.startMillis(recent), Days.endMillis(recent)))
    }

    @Test
    fun `without usage access, or for a stretch that ends before it starts, nothing is used`() {
        usm.addEvent("com.example.feed", start + hour, UsageEvents.Event.ACTIVITY_RESUMED)
        assertEquals(emptyMap<String, Long>(), tracker.usageBetween(end, start))
        assertEquals("com.example.notes", tracker.foregroundAfter("com.example.notes", end, start))
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        assertFalse(tracker.hasPermission())
        assertEquals(emptyMap<String, Long>(), tracker.usageBetween(start, end))
        assertEquals("com.example.notes", tracker.foregroundAfter("com.example.notes", start, end))
    }

    @Test
    fun `the app in front follows the log, and the screen going off leaves nothing in front`() {
        val now = System.currentTimeMillis()
        usm.addEvent("com.example.feed", now - 50_000, UsageEvents.Event.ACTIVITY_RESUMED)
        // Events Meadow doesn't read are passed over.
        usm.addEvent("com.example.feed", now - 45_000, UsageEvents.Event.CONFIGURATION_CHANGE)
        assertEquals("com.example.feed", tracker.foregroundAfter(null, now - 60_000, now))
        usm.addEvent("com.example.feed", now - 40_000, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
        assertEquals(null, tracker.foregroundAfter("com.example.feed", now - 45_000, now))
    }

    @Test
    fun `a recent stretch counts each way the screen ends`() {
        val now = System.currentTimeMillis()
        val from = now - 10 * 60_000
        usm.addEvent("com.example.feed", from + 60_000, UsageEvents.Event.ACTIVITY_RESUMED)
        usm.addEvent("com.example.feed", from + 2 * 60_000, UsageEvents.Event.KEYGUARD_SHOWN)
        usm.addEvent("com.example.chat", from + 3 * 60_000, UsageEvents.Event.ACTIVITY_RESUMED)
        usm.addEvent("com.example.chat", from + 4 * 60_000, 23) // Activity stopped.
        usm.addEvent("com.example.maps", from + 5 * 60_000, UsageEvents.Event.ACTIVITY_RESUMED)
        usm.addEvent("android", from + 6 * 60_000, 26) // Device shut down.
        usm.addEvent("android", from + 7 * 60_000, 27) // Device started.
        assertEquals(
            mapOf("com.example.feed" to 60_000L, "com.example.chat" to 60_000L, "com.example.maps" to 60_000L),
            tracker.usageBetween(from, now),
        )
    }

    @Test
    fun `an old day with its own daily total of nothing was quiet`() {
        bucket(UsageStatsManager.INTERVAL_DAILY, start, end, 0)
        assertEquals(emptyMap<String, Long>(), tracker.usageBetween(start, end))
    }

    @Test
    fun `an old day the log reaches only part of is counted from what it has`() {
        usm.addEvent("com.example.feed", start + hour, UsageEvents.Event.ACTIVITY_RESUMED)
        usm.addEvent("com.example.feed", start + 2 * hour, UsageEvents.Event.ACTIVITY_PAUSED)
        assertEquals(mapOf("com.example.feed" to hour), tracker.usageBetween(start, end))
    }
}
