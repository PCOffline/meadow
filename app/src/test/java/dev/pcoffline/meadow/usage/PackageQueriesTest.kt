package dev.pcoffline.meadow.usage

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The lookups answer the same with Int flags (Android 12L) and typed flags (Android 14). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.S_V2, Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PackageQueriesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val pm: PackageManager = app.packageManager
    private val main = ComponentName(PKG, "$PKG.Main")
    private val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Before
    fun install() {
        val appInfo = ApplicationInfo().apply {
            packageName = PKG
            nonLocalizedLabel = "Example"
        }
        shadowOf(pm).installPackage(PackageInfo().apply { packageName = PKG; applicationInfo = appInfo })
        val activity = ActivityInfo().apply {
            packageName = PKG
            name = main.className
            applicationInfo = appInfo
        }
        shadowOf(pm).addOrUpdateActivity(activity)
        shadowOf(pm).addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun `finds launchable activities`() {
        assertTrue(PKG in pm.queryActivities(launcher).map { it.activityInfo.packageName })
    }

    @Test
    fun `reads an app`() {
        assertEquals("Example", pm.getApplicationLabel(pm.applicationInfo(PKG)).toString())
        assertNotNull(pm.packageInfo(app.packageName))
    }

    // Robolectric only fakes the Int-flags getActivityInfo; the typed one asks a system service
    // it doesn't have, so this runs on the older version only.
    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `reads an activity`() {
        assertEquals(main.className, pm.activityInfo(main, PackageManager.MATCH_ALL).name)
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            pm.activityInfo(ComponentName(PKG, "$PKG.Missing"), PackageManager.MATCH_ALL)
        }
    }

    @Test
    fun `unknown apps are reported as not found`() {
        assertThrows(PackageManager.NameNotFoundException::class.java) { pm.applicationInfo("com.example.missing") }
        assertThrows(PackageManager.NameNotFoundException::class.java) { pm.packageInfo("com.example.missing") }
    }

    @Test
    fun `the catalog names launchable apps`() {
        assertEquals(listOf(InstalledApp(PKG, "Example")), AppCatalog(app).launchableApps().filter { it.packageName == PKG })
    }

    @Test
    fun `the catalog keeps its list until apps change`() {
        val catalog = AppCatalog(app)
        val first = catalog.launchableApps()
        // Read once: opening the picker again doesn't go through every app's name again.
        assertTrue(first === catalog.launchableApps())
        catalog.invalidate()
        assertTrue(first !== catalog.launchableApps())
        assertEquals(first, catalog.launchableApps())
    }

    private companion object {
        const val PKG = "com.example.reader"
    }
}
