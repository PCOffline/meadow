package dev.pcoffline.meadow.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches which app is on screen. When a limited app has no time left it is sent home and the
 * block screen appears. In strict mode it also keeps the settings pages that could switch
 * Meadow off out of reach.
 */
class GuardianAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    @Volatile private var currentPackage: String? = null
    private var lastStrictCheck = 0L
    private val activityCache = LruCache<String, Boolean>(256)

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        GuardianService.start(this)
        AppGraph.protection.check()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (AppGraph.store.strictMode.value && pkg in GUARDED_SYSTEM_PACKAGES) {
                    checkStrict(pkg, force = true)
                }
                if (isForegroundSwitch(pkg, event.className?.toString())) onForeground(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (AppGraph.store.strictMode.value && pkg in GUARDED_SYSTEM_PACKAGES) checkStrict(pkg, force = false)
            }
        }
    }

    /** Dialogs, keyboards and toasts also fire window events; only real activities count. */
    private fun isForegroundSwitch(pkg: String, className: String?): Boolean {
        if (pkg == "com.android.systemui") return false
        if (pkg in AppGraph.catalog.homePackages()) return true
        if (className == null) return false
        val key = "$pkg/$className"
        activityCache.get(key)?.let { return it }
        val isActivity = try {
            packageManager.getActivityInfo(ComponentName(pkg, className), PackageManager.MATCH_ALL)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        activityCache.put(key, isActivity)
        return isActivity
    }

    private fun onForeground(pkg: String) {
        if (pkg == currentPackage) return
        currentPackage = pkg
        AppGraph.engine.setForeground(pkg)
        watchJob?.cancel()
        if (pkg == packageName) return
        if (AppGraph.engine.limitFor(pkg) == null) return
        watchJob = scope.launch { watch(pkg) }
    }

    /** Re-checks the open app's budget, sleeping until it might run out. */
    private suspend fun watch(pkg: String) {
        var first = true
        while (currentCoroutineContext().isActive && currentPackage == pkg) {
            val budget = AppGraph.engine.budget(pkg, forceRefresh = first) ?: return
            first = false
            if (budget.exhausted) {
                withContext(Dispatchers.Main) { block(pkg) }
                return
            }
            if (budget.emergency) {
                // Wake up when the emergency unlock ends, or sooner if it's ended early.
                delay((budget.emergencyUntilMillis - System.currentTimeMillis()).coerceIn(1_000L, 15_000L))
                continue
            }
            AppGraph.notifier.maybeWarn(budget)
            delay(budget.remainingMs.coerceIn(1_000L, 15_000L))
        }
    }

    private fun block(pkg: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockActivity.launch(this, pkg)
    }

    private fun checkStrict(pkg: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStrictCheck < 700) return
        lastStrictCheck = now
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text, IntArray(1) { 600 })
        val screen = text.toString().lowercase()
        if (StrictModeRules.shouldBlock(pkg, screen, appLabel = getString(R.string.app_name).lowercase())) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, R.string.strict_mode_blocked, Toast.LENGTH_LONG).show()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, budget: IntArray) {
        if (node == null || budget[0] <= 0) return
        budget[0]--
        node.text?.let { out.append(it).append('\n') }
        node.contentDescription?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, budget)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        AppGraph.protection.check()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set

        val GUARDED_SYSTEM_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}

/** Decides which system screens are off limits while strict mode is on. */
object StrictModeRules {
    private val dangerousNearApp = listOf(
        "force stop", "uninstall", "clear storage", "clear data", "deactivate", "turn off",
        "use meadow", "stop meadow", "permit usage access", "usage access", "disable",
    )
    private val clockKeywords = listOf("set time", "set date", "automatic date", "use network-provided time")

    fun shouldBlock(pkg: String, screenText: String, appLabel: String): Boolean {
        if (clockKeywords.any { it in screenText } && pkg.contains("settings")) return true
        if (appLabel !in screenText) return false
        return dangerousNearApp.any { it in screenText }
    }
}
