package dev.pcoffline.meadow.usage

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

// PackageManager lookups with Int flags. Android 13 deprecated the Int overloads in favour of
// typed flags, so these use the typed ones there and the Int ones on older versions.

fun PackageManager.queryActivities(intent: Intent, flags: Int = 0): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") queryIntentActivities(intent, flags)
    }

/** @throws PackageManager.NameNotFoundException if there is no such activity. */
fun PackageManager.activityInfo(component: ComponentName, flags: Int = 0): ActivityInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getActivityInfo(component, PackageManager.ComponentInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getActivityInfo(component, flags)
    }

/** @throws PackageManager.NameNotFoundException if the app isn't installed. */
fun PackageManager.applicationInfo(packageName: String, flags: Int = 0): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getApplicationInfo(packageName, flags)
    }

/** @throws PackageManager.NameNotFoundException if the app isn't installed. */
fun PackageManager.packageInfo(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }

/** The package inside the APK at [path], or null if it can't be read. */
fun PackageManager.packageArchiveInfo(path: String, flags: Int = 0): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageArchiveInfo(path, flags)
    }
