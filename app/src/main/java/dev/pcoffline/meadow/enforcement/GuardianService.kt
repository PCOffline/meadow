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
import dev.pcoffline.meadow.data.formatDuration
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = AppGraph.notifier.guardianNotification("Tending your garden 🌱")
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
                if (!GuardianAccessibilityService.running) pollForeground()
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
        val pkg = AppGraph.tracker.currentForegroundApp() ?: return
        AppGraph.engine.setForeground(pkg)
        val budget = AppGraph.engine.budget(pkg) ?: return
        if (!budget.exhausted) {
            AppGraph.notifier.maybeWarn(budget)
            return
        }
        val now = System.currentTimeMillis()
        val last = lastBlocked
        if (last != null && last.first == pkg && now - last.second < 4_000L) return
        lastBlocked = pkg to now
        // Starting an activity from the background requires the overlay permission.
        if (Settings.canDrawOverlays(this)) BlockActivity.launch(this, pkg)
        else AppGraph.notifier.timeUp(budget)
    }

    private fun updateSummary() {
        val budgets = AppGraph.engine.budgets()
        val text = when {
            budgets.isEmpty() -> "No limits yet — plant one in Meadow 🌱"
            else -> {
                val open = budgets.filter { !it.exhausted }.minByOrNull { it.remainingMs }
                val done = budgets.count { it.exhausted }
                buildString {
                    if (open != null) append("${open.limit.appName}: ${formatDuration(open.remainingMs)} left")
                    if (done > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("$done resting until tomorrow")
                    }
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
