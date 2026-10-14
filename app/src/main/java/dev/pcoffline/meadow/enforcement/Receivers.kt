package dev.pcoffline.meadow.enforcement

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.work.SyncWorker

/** Starts the guardian again after the phone restarts or Meadow is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts, so any app could also call it directly with
        // another action. Only the two the manifest declares, both sent only by the system, count.
        if (!handles(intent.action)) return
        GuardianService.start(context)
        SyncWorker.schedule(context)
    }

    companion object {
        private val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        internal fun handles(action: String?): Boolean = action in ACTIONS
    }
}

/** Device admin only exists to make uninstalling Meadow a deliberate, visible act. */
class MeadowDeviceAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    override fun onDisabled(context: Context, intent: Intent) {
        AppGraph.protection.check(AppGraph.protection.current().copy(deviceAdmin = false))
    }

    override fun onEnabled(context: Context, intent: Intent) {
        AppGraph.protection.check()
    }
}
