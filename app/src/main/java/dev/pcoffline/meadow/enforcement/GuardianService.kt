package dev.pcoffline.meadow.enforcement

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps Meadow alive with a quiet ongoing notification, refreshes the "time left" summary, keeps
 * an eye on protections, and polls the foreground app itself whenever the accessibility
 * keeper is not running.
 */
class GuardianService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBlocked: Pair<String, Long>? = null
    private var foreground: String? = null
    private var probedUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = AppGraph.notifier.guardianNotification(getString(R.string.guardian_starting))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifier.GUARDIAN_ID, notification, type)
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private suspend fun loop() {
        var tick = 0
        while (scope.isActive) {
            runCatching {
                if (GuardianAccessibilityService.running) {
                    probedUntil = 0L
                    foreground = null
                } else {
                    pollForeground()
                }
                if (tick % 12 == 0) {
                    updateSummary()
                    AppGraph.protection.check()
                }
            }.onFailure { Log.w(TAG, "Guardian tick failed", it) }
            tick++
            delay(5_000L)
        }
    }

    /** Fallback enforcement through the usage log when accessibility is off. */
    private fun pollForeground() {
        val now = System.currentTimeMillis()
        // The first probe looks back an hour; later ones only read events since the previous probe.
        val from = if (probedUntil == 0L) now - 60 * 60_000L else probedUntil
        foreground = AppGraph.tracker.foregroundAfter(foreground, from, now)
        probedUntil = now
        val pkg = foreground ?: return
        AppGraph.engine.setForeground(pkg)
        val budget = AppGraph.engine.budget(pkg) ?: return
        if (!budget.exhausted) {
            if (!budget.emergency) AppGraph.notifier.maybeWarn(budget)
            return
        }
        val last = lastBlocked
        if (last != null && last.first == pkg && now - last.second < 4_000L) return
        lastBlocked = pkg to now
        // Starting an activity from the background requires the overlay permission.
        if (Settings.canDrawOverlays(this)) BlockActivity.launch(this, pkg)
        else AppGraph.notifier.timeUp(budget)
    }

    private fun updateSummary() {
        val budgets = AppGraph.engine.budgets()
        val emergency = AppGraph.store.emergency.value
        val text = when {
            emergency?.isActive() == true ->
                getString(R.string.guardian_emergency, resources.durationText(emergency.untilMillis - System.currentTimeMillis()))
            budgets.isEmpty() -> getString(R.string.guardian_no_limits)
            else -> {
                val open = budgets.filter { !it.exhausted && it.remainingMs != Long.MAX_VALUE }.minByOrNull { it.remainingMs }
                val resting = budgets.count { it.exhausted }
                val left = open?.let { getString(R.string.guardian_left, it.limit.appName, resources.durationText(it.remainingMs)) }
                val sleeping = resting.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.guardian_resting, it, it) }
                when {
                    left != null && sleeping != null -> getString(R.string.guardian_joined, left, sleeping)
                    else -> left ?: sleeping ?: getString(R.string.guardian_starting)
                }
            }
        }
        AppGraph.notifier.updateGuardian(text)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MeadowGuardian"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, GuardianService::class.java))
            }.onFailure { Log.w(TAG, "Could not start guardian", it) }
        }
    }
}
