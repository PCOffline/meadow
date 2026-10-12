package dev.pcoffline.meadow.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.telecom.TelecomManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledApp(val packageName: String, val label: String)

/** Knows which apps are launchable, their names and icons. */
class AppCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labelCache = HashMap<String, String>()
    private val iconCache = LruCache<String, ImageBitmap>(120)

    @Volatile private var launchableCache: Set<String>? = null
    @Volatile private var homeCache: Set<String>? = null
    @Volatile private var essentialCache: Set<String>? = null

    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName && it !in essentialPackages() && it !in homePackages() }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun launchablePackages(): Set<String> =
        launchableCache ?: launchableApps().map { it.packageName }.toSet().also { launchableCache = it }

    /** Launchers and the system UI never count as "app usage" and can never be limited. */
    fun homePackages(): Set<String> = homeCache ?: run {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homes = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        (homes + setOf("com.android.systemui", context.packageName)).also { homeCache = it }
    }

    /** The phone, dialer and emergency apps: always reachable, never limitable. */
    fun essentialPackages(): Set<String> = essentialCache ?: run {
        val dialers = pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0).map { it.activityInfo.packageName }
        val defaultDialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        (dialers + listOfNotNull(defaultDialer) + ALWAYS_ALLOWED).toSet().also { essentialCache = it }
    }

    /** Keeps only user-facing apps, dropping launchers, system UI and this app. */
    fun userFacing(usage: Map<String, Long>): Map<String, Long> {
        val launchable = launchablePackages()
        val homes = homePackages()
        return usage.filter { (pkg, ms) -> ms > 0 && pkg in launchable && pkg !in homes }
    }

    fun label(packageName: String): String = labelCache.getOrPut(packageName) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    fun icon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val size = 96
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        }
        return bitmap.asImageBitmap().also { iconCache.put(packageName, it) }
    }

    /** Forgets cached app lists; called when apps are installed or removed. */
    fun invalidate() {
        launchableCache = null
        homeCache = null
        essentialCache = null
    }

    private companion object {
        val ALWAYS_ALLOWED = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
        )
    }
}
