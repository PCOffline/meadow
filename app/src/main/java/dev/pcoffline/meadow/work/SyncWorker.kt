package dev.pcoffline.meadow.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.SharedAppUsage
import dev.pcoffline.meadow.data.SharedUsage
import dev.pcoffline.meadow.data.runCatchingCancellable
import dev.pcoffline.meadow.enforcement.GuardianService
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: restarts the guardian if it was killed and shares today's usage with
 * approvers. About once an hour it also sends a heartbeat so they notice if Meadow goes quiet
 * (Bumblebees are told after six quiet hours; protection changes are sent straight away).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        GuardianService.start(applicationContext)
        if (!AppGraph.backendReady || AppGraph.backend.uid == null) return Result.success()
        return runCatchingCancellable {
            AppGraph.emergency.flushPending()
            val privacy = AppGraph.store.privacy.value
            if (privacy.shareUsage) AppGraph.share.upload(todaySummary())
            val now = System.currentTimeMillis()
            if (now - AppGraph.store.lastHeartbeatMillis >= HEARTBEAT_EVERY_MS) {
                AppGraph.share.heartbeat(if (privacy.shareProtection) AppGraph.protection.current().asMap() else emptyMap())
                AppGraph.store.lastHeartbeatMillis = now
            }
            AppGraph.protection.check()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "meadow-sync"
        private const val HEARTBEAT_EVERY_MS = 55 * 60_000L

        fun todaySummary(): SharedUsage {
            val usage = AppGraph.engine.todayUsage(forceRefresh = true)
            val limits = AppGraph.store.limits.value.associateBy { it.packageName }
            val apps = AppGraph.catalog.userFacing(usage)
                .entries
                .sortedByDescending { it.value }
                .take(15)
                .map { (pkg, ms) ->
                    SharedAppUsage(
                        packageName = pkg,
                        appName = AppGraph.catalog.label(pkg),
                        minutes = (ms / 60_000L).toInt(),
                        limitMinutes = limits[pkg]?.dailyMinutes ?: 0,
                    )
                }
            val total = AppGraph.catalog.userFacing(usage).values.sum()
            return SharedUsage(Days.todayKey(), (total / 60_000L).toInt(), apps)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
