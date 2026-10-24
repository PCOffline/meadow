package dev.pcoffline.meadow.usage

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Finished days are read from the system once and kept; today is always read afresh. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class UsageHistoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.filesDir, "usage_history.json")

    @Before
    fun clean() {
        file.delete()
    }

    private fun history() = UsageHistory(context, UsageTracker(context), AppCatalog(context))

    private fun stored(): String = if (file.exists()) file.readText() else ""

    @Test
    fun `a finished day without usage is kept, so it isn't looked up again`() {
        val quiet = Days.today().minusDays(2)
        assertEquals(emptyMap<String, Long>(), history().day(quiet))
        assertTrue(stored().contains("\"${Days.key(quiet)}\":{}"))
    }

    @Test
    fun `today is never kept`() {
        history().day(Days.today())
        assertFalse(stored().contains(Days.todayKey()))
    }

    @Test
    fun `without usage access an empty day is not kept`() {
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        val unread = Days.today().minusDays(2)
        assertEquals(emptyMap<String, Long>(), history().day(unread))
        assertFalse(stored().contains(Days.key(unread)))
    }
}
