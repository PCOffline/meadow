package dev.pcoffline.meadow

import android.app.Application
import dev.pcoffline.meadow.enforcement.GuardianService
import dev.pcoffline.meadow.work.SyncWorker

class MeadowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppGraph.notifier.createChannels()
        if (AppGraph.backendReady) AppGraph.sync.start()
        SyncWorker.schedule(this)
        GuardianService.start(this)
    }
}
