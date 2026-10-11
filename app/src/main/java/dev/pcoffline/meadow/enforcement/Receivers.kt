package dev.pcoffline.meadow.enforcement

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.work.SyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GuardianService.start(context)
        SyncWorker.schedule(context)
    }
}

/** Device admin only exists to make uninstalling Meadow a deliberate, visible act. */
class MeadowDeviceAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Meadow be uninstalled. Your approvers will be told. 🌷"

    override fun onDisabled(context: Context, intent: Intent) {
        AppGraph.protection.check(AppGraph.protection.current().copy(deviceAdmin = false))
    }

    override fun onEnabled(context: Context, intent: Intent) {
        AppGraph.protection.check()
    }
}
