package dev.pcoffline.meadow

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import dev.pcoffline.meadow.enforcement.GuardianService
import dev.pcoffline.meadow.update.UpdateCheckWorker
import dev.pcoffline.meadow.work.SyncWorker

class MeadowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Meadow's own emoji font for every emoji, so flowers and icons look the same on every
        // phone rather than each maker's own (or missing) version. Compose text picks it up.
        EmojiCompat.init(BundledEmojiCompatConfig(this).setReplaceAll(true))
        AppGraph.init(this)
        AppGraph.notifier.createChannels()
        if (AppGraph.backendReady) AppGraph.sync.start()
        SyncWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        GuardianService.start(this)
        watchInstalledApps()
    }

    /** New or removed apps change what can be limited and what counts as screen time. */
    private fun watchInstalledApps() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = AppGraph.catalog.invalidate()
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
