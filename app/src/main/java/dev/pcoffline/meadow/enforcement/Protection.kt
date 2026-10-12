package dev.pcoffline.meadow.enforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.UsageShareRepository
import dev.pcoffline.meadow.data.runCatchingCancellable
import dev.pcoffline.meadow.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ProtectionState(
    val usageAccess: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val deviceAdmin: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** The pieces without which limits cannot be enforced at all. */
    val essentialsReady: Boolean get() = usageAccess && accessibility

    fun asMap(): Map<String, Boolean> = mapOf(
        "usageAccess" to usageAccess,
        "accessibility" to accessibility,
        "notifications" to notifications,
        "overlay" to overlay,
        "deviceAdmin" to deviceAdmin,
        "batteryUnrestricted" to batteryUnrestricted,
    )

    companion object {
        /** Protections whose loss Bumblebees hear about. Names match the backend's copy.ts. */
        val REPORTED = listOf("usageAccess", "accessibility", "deviceAdmin")
    }
}

/**
 * Reads which protections are switched on and tells Bumblebees when one that matters is
 * switched off while limits are in place.
 */
class ProtectionMonitor(
    private val context: Context,
    private val tracker: UsageTracker,
    private val store: LocalStore,
    private val share: UsageShareRepository,
    private val scope: CoroutineScope,
    private val backendReady: Boolean,
) {
    fun current(): ProtectionState = ProtectionState(
        usageAccess = tracker.hasPermission(),
        accessibility = isAccessibilityEnabled(),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        overlay = Settings.canDrawOverlays(context),
        deviceAdmin = isDeviceAdminActive(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
    )

    fun adminComponent() = ComponentName(context, MeadowDeviceAdmin::class.java)

    fun isDeviceAdminActive(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = ComponentName(context, GuardianAccessibilityService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c == ours } == true
        }
    }

    /** Compares with the last reported state, alerts Bumblebees on anything switched off. */
    fun check(override: ProtectionState? = null) {
        if (!backendReady) return
        val state = override ?: current()
        val map = state.asMap()
        val previous = store.lastReportedProtection
            ?.split(',')
            ?.mapNotNull { entry -> entry.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1] == "1") } }
            ?.toMap()
        val encoded = map.entries.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" }
        if (encoded == store.lastReportedProtection) return
        store.lastReportedProtection = encoded
        scope.launch {
            runCatchingCancellable { share.heartbeat(map) }
            if (previous == null || store.limits.value.isEmpty()) return@launch
            val lost = ProtectionState.REPORTED.filter { previous[it] == true && map[it] == false }
            if (lost.isNotEmpty()) runCatchingCancellable { share.reportProtectionsLost(lost) }
        }
    }
}
