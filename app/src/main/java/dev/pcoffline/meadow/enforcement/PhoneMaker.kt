package dev.pcoffline.meadow.enforcement

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.net.toUri
import dev.pcoffline.meadow.R

/**
 * Many phones stop background apps beyond what Android itself does, each in its own settings
 * screen. These are the extra steps that keep Meadow running, for the phone in hand.
 */
object PhoneMaker {

    /** One thing to change. [screens] are tried in order; none means it's done by hand. */
    data class Step(
        val key: String,
        @StringRes val title: Int,
        @StringRes val body: Int,
        val screens: List<Intent> = emptyList(),
    )

    enum class Brand(val slug: String) { XIAOMI("xiaomi"), HUAWEI("huawei"), SAMSUNG("samsung"), OPPO("oppo"), ONEPLUS("oneplus"), VIVO("vivo"), ASUS("asus"), OTHER("") }

    fun brand(manufacturer: String = Build.MANUFACTURER, model: String = Build.BRAND): Brand {
        val m = "${manufacturer.lowercase()} ${model.lowercase()}"
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Brand.XIAOMI
            listOf("huawei", "honor").any { it in m } -> Brand.HUAWEI
            "samsung" in m -> Brand.SAMSUNG
            "oneplus" in m -> Brand.ONEPLUS
            listOf("oppo", "realme").any { it in m } -> Brand.OPPO
            listOf("vivo", "iqoo").any { it in m } -> Brand.VIVO
            "asus" in m -> Brand.ASUS
            else -> Brand.OTHER
        }
    }

    /** Where to read more about this phone's habits. */
    fun helpUrl(brand: Brand): String = if (brand == Brand.OTHER) "https://dontkillmyapp.com" else "https://dontkillmyapp.com/${brand.slug}"

    fun steps(context: Context, brand: Brand = brand()): List<Step> {
        val pkg = context.packageName
        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg".toUri())
        @Suppress("BatteryLife") // Meadow enforces limits in the background; that's its whole job.
        val battery = Step(
            "battery", R.string.trouble_battery_title, R.string.trouble_battery_body,
            listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$pkg".toUri()), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
        )
        val background = Step("background", R.string.trouble_background_title, R.string.trouble_background_body, listOf(appInfo))
        val recents = Step("recents", R.string.trouble_lock_title, R.string.trouble_lock_body)
        val specific = when (brand) {
            Brand.XIAOMI -> listOf(
                Step(
                    "autostart", R.string.trouble_autostart_title, R.string.trouble_xiaomi_autostart_body,
                    listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), appInfo),
                ),
                Step(
                    "saver", R.string.trouble_saver_title, R.string.trouble_xiaomi_saver_body,
                    listOf(
                        component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                            .putExtra("package_name", pkg).putExtra("package_label", context.getString(R.string.app_name)),
                        appInfo,
                    ),
                ),
                Step(
                    "popup", R.string.trouble_popup_title, R.string.trouble_xiaomi_popup_body,
                    listOf(
                        component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", pkg),
                        appInfo,
                    ),
                ),
                recents,
            )
            Brand.HUAWEI -> listOf(
                Step(
                    "autostart", R.string.trouble_autostart_title, R.string.trouble_huawei_body,
                    listOf(
                        component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                        appInfo,
                    ),
                ),
                recents,
            )
            Brand.SAMSUNG -> listOf(
                Step(
                    "sleeping", R.string.trouble_samsung_title, R.string.trouble_samsung_body,
                    listOf(component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"), appInfo),
                ),
            )
            Brand.OPPO, Brand.ONEPLUS -> listOf(
                Step(
                    "autostart", R.string.trouble_autostart_title, R.string.trouble_oppo_body,
                    listOf(
                        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                        component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                        appInfo,
                    ),
                ),
                recents,
            )
            Brand.VIVO -> listOf(
                Step(
                    "autostart", R.string.trouble_autostart_title, R.string.trouble_vivo_body,
                    listOf(
                        component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        appInfo,
                    ),
                ),
                recents,
            )
            Brand.ASUS -> listOf(
                Step(
                    "autostart", R.string.trouble_autostart_title, R.string.trouble_asus_body,
                    listOf(
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
                        appInfo,
                    ),
                ),
            )
            Brand.OTHER -> emptyList()
        }
        return listOf(battery, background) + specific
    }

    /** Opens the first screen of [step] this phone has. False when there's none to open. */
    fun open(context: Context, step: Step): Boolean = step.screens.any { intent ->
        runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
}
