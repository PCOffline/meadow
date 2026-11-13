package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.enforcement.PhoneMaker.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The extra steps that keep Meadow running on phones that stop background apps. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PhoneMakerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the brand is found from the maker or the brand name`() {
        val cases = mapOf(
            ("Xiaomi" to "Redmi") to Brand.XIAOMI,
            ("unknown" to "POCO") to Brand.XIAOMI,
            ("HONOR" to "honor") to Brand.HUAWEI,
            ("samsung" to "samsung") to Brand.SAMSUNG,
            ("OnePlus" to "OnePlus") to Brand.ONEPLUS,
            ("realme" to "realme") to Brand.OPPO,
            ("vivo" to "iQOO") to Brand.VIVO,
            ("asus" to "asus") to Brand.ASUS,
            ("Google" to "google") to Brand.OTHER,
        )
        for ((names, brand) in cases) assertEquals("$names", brand, PhoneMaker.brand(names.first, names.second))
    }

    @Test
    fun `the help page is the brand's own, or the general one`() {
        assertEquals("https://dontkillmyapp.com/samsung", PhoneMaker.helpUrl(Brand.SAMSUNG))
        assertEquals("https://dontkillmyapp.com", PhoneMaker.helpUrl(Brand.OTHER))
    }

    @Test
    fun `every phone gets the battery and background steps, and each brand its own`() {
        val expected = mapOf(
            Brand.XIAOMI to listOf("autostart", "saver", "popup", "recents"),
            Brand.HUAWEI to listOf("autostart", "recents"),
            Brand.SAMSUNG to listOf("sleeping"),
            Brand.OPPO to listOf("autostart", "recents"),
            Brand.ONEPLUS to listOf("autostart", "recents"),
            Brand.VIVO to listOf("autostart", "recents"),
            Brand.ASUS to listOf("autostart"),
            Brand.OTHER to emptyList(),
        )
        for ((brand, own) in expected) {
            assertEquals("$brand", listOf("battery", "background") + own, PhoneMaker.steps(app, brand).map { it.key })
        }
    }

    @Test
    fun `a step opens the first screen the phone has`() {
        shadowOf(app).checkActivities(true)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            details.setData(android.net.Uri.parse("package:${app.packageName}")),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "AppDetails" } },
        )
        // Xiaomi's own autostart screen isn't on this phone, so the app's details open instead.
        val autostart = PhoneMaker.steps(app, Brand.XIAOMI).single { it.key == "autostart" }

        assertTrue(PhoneMaker.open(app, autostart))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a step done by hand has nothing to open`() {
        val recents = PhoneMaker.steps(app, Brand.XIAOMI).single { it.key == "recents" }
        assertFalse(PhoneMaker.open(app, recents))
    }
}
