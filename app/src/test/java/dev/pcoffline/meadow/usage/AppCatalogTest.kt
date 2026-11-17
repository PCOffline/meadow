package dev.pcoffline.meadow.usage

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The apps a person can limit, their names and their icons, as the phone has them. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppCatalogTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val catalog = AppCatalog(app)
    private val size get() = (56 * app.resources.displayMetrics.density).toInt()

    private fun install(pkg: String, label: String? = null) {
        val pm = shadowOf(app.packageManager)
        val info = ApplicationInfo().apply { packageName = pkg; label?.let { nonLocalizedLabel = it } }
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        val main = ComponentName(pkg, "$pkg.Main")
        pm.addOrUpdateActivity(ActivityInfo().apply { packageName = pkg; name = main.className; applicationInfo = info })
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun `a small bitmap icon is used as it is, and kept`() {
        install("com.example.small")
        val bitmap = Bitmap.createBitmap(size / 2, size / 2, Bitmap.Config.ARGB_8888)
        shadowOf(app.packageManager).setApplicationIcon("com.example.small", BitmapDrawable(app.resources, bitmap))
        val icon = catalog.icon("com.example.small")!!
        assertEquals(size / 2, icon.width)
        assertSame(icon, catalog.cachedIcon("com.example.small"))
        assertSame(icon, catalog.icon("com.example.small"))
    }

    @Test
    fun `a large or drawn icon is drawn at the size shown`() {
        install("com.example.large")
        shadowOf(app.packageManager).setApplicationIcon("com.example.large", BitmapDrawable(app.resources, Bitmap.createBitmap(size * 4, size * 4, Bitmap.Config.ARGB_8888)))
        assertEquals(size, catalog.icon("com.example.large")!!.width)
        install("com.example.drawn")
        shadowOf(app.packageManager).setApplicationIcon("com.example.drawn", ColorDrawable(0xFF336699.toInt()))
        assertEquals(size, catalog.icon("com.example.drawn")!!.width)
    }

    @Test
    fun `an app that isn't installed has no icon, and a name from its package`() {
        assertNull(catalog.icon("com.example.gone"))
        assertNull(catalog.cachedIcon("com.example.gone"))
        assertEquals("Gone", catalog.label("com.example.gone"))
        assertEquals("youtube.com", catalog.label("site:youtube.com"))
    }

    @Test
    fun `only launchable apps that were used, other than launchers and Meadow, count as used`() {
        install("com.example.used", "Used")
        install("com.example.idle", "Idle")
        catalog.invalidate()
        val usage = mapOf("com.example.used" to 60_000L, "com.example.idle" to 0L, "com.example.gone" to 60_000L, app.packageName to 60_000L)
        assertEquals(mapOf("com.example.used" to 60_000L), catalog.userFacing(usage))
        assertEquals(listOf("Idle", "Used"), catalog.launchableApps().map { it.label }.filter { it in setOf("Idle", "Used") })
    }
}
