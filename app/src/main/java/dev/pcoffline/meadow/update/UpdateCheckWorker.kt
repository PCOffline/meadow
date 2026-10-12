package dev.pcoffline.meadow.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pcoffline.meadow.AppGraph
import java.util.concurrent.TimeUnit

/** Once a day, if automatic updates are on, looks for a new release and offers it in a notification. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AppGraph.store.autoUpdate.value) return Result.success()
        val update = AppGraph.updater.check() ?: return Result.success()
        val version = update.version.toString()
        if (AppGraph.store.notifiedUpdate != version) {
            AppGraph.notifier.updateAvailable(version)
            AppGraph.store.notifiedUpdate = version
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "meadow-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
