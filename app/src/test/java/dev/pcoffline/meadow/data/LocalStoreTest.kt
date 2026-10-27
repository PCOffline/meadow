package dev.pcoffline.meadow.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The on-device copy enforcement relies on must survive restarts and be wiped completely on deletion. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LocalStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `fresh installs start with safe defaults`() {
        val store = LocalStore(context)
        assertEquals(emptyList<AppLimit>(), store.limits.value)
        assertFalse(store.strictMode.value)
        assertTrue(store.autoUpdate.value)
        assertEquals(Privacy(), store.privacy.value)
        assertEquals(0, store.acceptedTerms.value)
        assertNull(store.theme.value)
    }

    @Test
    fun `the first day of the week, and a change still waiting, survive a restart and a wipe`() {
        val wednesday = java.time.LocalDate.of(2026, 10, 28)
        LocalStore(context).apply {
            setLimits(listOf(AppLimit("com.example.app", "Example", dailyMinutes = 0, weeklyMinutes = 300)))
            setWeekStart(java.time.DayOfWeek.SUNDAY, wednesday)
        }
        val restarted = LocalStore(context)
        assertEquals(WeekRule().changedTo(java.time.DayOfWeek.SUNDAY, wednesday), restarted.weekRule.value)
        restarted.clearAll()
        assertEquals(WeekRule(), LocalStore(context).weekRule.value)
        assertFalse(LocalStore(context).weekStartSet)
    }

    @Test
    fun `without a weekly limit, a new first day applies at once`() {
        val store = LocalStore(context)
        store.setLimits(listOf(AppLimit("com.example.app", "Example", dailyMinutes = 30, weeklyMinutes = 0)))
        store.setWeekStart(java.time.DayOfWeek.SATURDAY, java.time.LocalDate.of(2026, 10, 28))
        assertEquals(WeekRule(java.time.DayOfWeek.SATURDAY), store.weekRule.value)
    }

    @Test
    fun `a first run takes the region's first day, but never overrides one already set`() {
        val fresh = LocalStore(context)
        assertFalse(fresh.weekStartSet)
        assertEquals(WeekRule(), fresh.weekRule.value)
        fresh.adoptWeekStart(java.time.DayOfWeek.SUNDAY)
        assertTrue(fresh.weekStartSet)
        assertEquals(WeekRule(java.time.DayOfWeek.SUNDAY), LocalStore(context).weekRule.value)

        fresh.setWeekStart(java.time.DayOfWeek.MONDAY)
        fresh.adoptWeekStart(java.time.DayOfWeek.SATURDAY)
        assertEquals(java.time.DayOfWeek.MONDAY, LocalStore(context).weekRule.value.first)
    }

    @Test
    fun `the region decides the first day, among Saturday, Sunday and Monday`() {
        assertEquals(java.time.DayOfWeek.SUNDAY, Days.regionalWeekStart(java.util.Locale.US))
        assertEquals(java.time.DayOfWeek.MONDAY, Days.regionalWeekStart(java.util.Locale.UK))
        assertEquals(java.time.DayOfWeek.MONDAY, Days.regionalWeekStart(java.util.Locale.GERMANY))
        assertEquals(java.time.DayOfWeek.SATURDAY, Days.regionalWeekStart(java.util.Locale.forLanguageTag("ar-EG")))
        // The Maldives start on Friday, which Meadow doesn't offer: Monday, the international standard.
        assertEquals(java.time.DayOfWeek.MONDAY, Days.regionalWeekStart(java.util.Locale.forLanguageTag("dv-MV")))
    }

    @Test
    fun `a privacy choice saved before the split still holds after the update`() {
        context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().putBoolean("share_usage", false).commit()
        assertEquals(Privacy(shareTime = false, shareApps = false), LocalStore(context).privacy.value)
    }

    @Test
    fun `commitments survive a process restart`() {
        val limits = listOf(AppLimit("com.insta", "Instagram", 30, 0), AppLimit("com.tok", "TikTok", 0, 120))
        val grants = listOf(Grant("r1", "com.insta", 15, "2026-10-12"))
        LocalStore(context).apply {
            setLimits(limits)
            setGrants(grants)
            setStrictMode(true)
            setEmergency(Emergency(5_000L, "com.insta"))
            setPrivacy(Privacy(shareApps = false, shareLimits = false))
            setAppearance("pond", "dark")
            setAutoUpdate(false)
            acceptedTermsVersion = 1
            pendingEmergency = PendingEmergency(15, "Lost and need maps", null, null, 1_000L)
        }

        val restarted = LocalStore(context)
        assertEquals(limits, restarted.limits.value)
        assertEquals(grants, restarted.grants.value)
        assertTrue(restarted.strictMode.value)
        assertEquals(Emergency(5_000L, "com.insta"), restarted.emergency.value)
        assertEquals(Privacy(shareApps = false, shareLimits = false), restarted.privacy.value)
        assertEquals("pond", restarted.theme.value)
        assertEquals("dark", restarted.darkMode.value)
        assertFalse(restarted.autoUpdate.value)
        assertEquals(1, restarted.acceptedTerms.value)
        assertEquals(15, restarted.pendingEmergency?.minutes)
    }

    @Test
    fun `clearAll forgets everything, in memory and on disk`() {
        val store = LocalStore(context).apply {
            setLimits(listOf(AppLimit("com.insta", "Instagram", 30, 0)))
            setStrictMode(true)
            setPendingInvite("AbCdEfGh12345678")
            setAutoUpdate(false)
            acceptedTermsVersion = 1
            notifiedUpdate = "1.2.0"
        }
        store.clearAll()

        for (s in listOf(store, LocalStore(context))) {
            assertEquals(emptyList<AppLimit>(), s.limits.value)
            assertFalse(s.strictMode.value)
            assertNull(s.pendingInvite.value)
            assertTrue(s.autoUpdate.value)
            assertEquals(0, s.acceptedTerms.value)
            assertNull(s.notifiedUpdate)
        }
    }

    @Test
    fun `a corrupted entry reads as empty instead of crashing enforcement`() {
        context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().putString("limits_v2", "{not json").commit()
        assertEquals(emptyList<AppLimit>(), LocalStore(context).limits.value)
    }
}
