package dev.pcoffline.meadow.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
 * Watches which apps are on screen, including split-screen halves and picture-in-picture. When
 * a limited app has no time left its window is closed, the block screen appears, its
 * notifications and media stop and its process is ended once in the background. In strict mode
 * it also keeps the settings pages that could switch Meadow off out of reach.
 */
class GuardianAccessibilityService : AccessibilityService() {
    /** Background work: budget watchers and strict-mode reads; internal so tests can wait for it. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var currentPackage: String? = null
    private var lastStrictCheck = 0L

    /** The strict-mode read of the screen in progress, and a screen to read once it ends. */
    private var strictJob: Job? = null
    private var strictRecheck: String? = null
    private val activityCache = LruCache<String, Boolean>(256)

    /** Limited apps on screen besides the main one: the other split-screen half, a floating window. */
    @Volatile private var sidePackages: Set<String> = emptySet()

    /** The app shown in picture-in-picture, taken to be the limited app left just before it appeared. */
    @Volatile private var pipPackage: String? = null
    private var lastLimitedLeft: String? = null
    private val watchers = HashMap<String, Job>()
    private val windowPackages = WindowPackages()

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        GuardianService.start(this)
        AppGraph.protection.check()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scanWindows()
            return
        }
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
        currentPackage?.takeIf { AppGraph.engine.limitFor(it) != null }?.let { lastLimitedLeft = it }
        currentPackage = pkg
        AppGraph.engine.setForeground(pkg)
        // Picture-in-picture often starts just as its app is left.
        scanWindows()
    }

    private fun onScreen(pkg: String) = pkg == currentPackage || pkg in sidePackages || pkg == pipPackage

    /** Finds limited apps in other windows: split screen and picture-in-picture. */
    private fun scanWindows() {
        val all = runCatching { windows }.getOrNull() ?: return
        var pip = false
        val apps = ArrayList<AccessibilityWindowInfo>()
        for (w in all) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (w.isInPictureInPictureMode) {
                pip = true
                continue
            }
            apps += w
        }
        // Reading a window's root asks its app, so each window is only asked once.
        val side = HashSet<String>()
        for (pkg in windowPackages.of(apps, { it.id }) { w -> runCatching { w.root?.packageName?.toString() }.getOrNull() }) {
            if (pkg != currentPackage && pkg != packageName && AppGraph.engine.limitFor(pkg) != null) side += pkg
        }
        pipPackage = if (!pip) null else pipPackage ?: lastLimitedLeft?.takeIf { it != currentPackage }
        sidePackages = side
        updateWatchers()
    }

    /** One watcher per limited app on screen; watchers of apps no longer shown stop. */
    @Synchronized
    private fun updateWatchers() {
        val shown = (sidePackages + listOfNotNull(currentPackage, pipPackage))
            .filter { it != packageName && AppGraph.engine.limitFor(it) != null }
            .toSet()
        watchers.entries.removeAll { (pkg, job) -> (pkg !in shown || !job.isActive).also { gone -> if (gone) job.cancel() } }
        for (pkg in shown) if (pkg !in watchers) watchers[pkg] = scope.launch { watch(pkg) }
    }

    /** Re-checks an open app's budget, sleeping until it might run out. */
    private suspend fun watch(pkg: String) {
        var first = true
        val power = getSystemService(PowerManager::class.java)
        while (currentCoroutineContext().isActive && onScreen(pkg)) {
            // The screen went off on this app: nothing is being used until it's back on.
            if (!power.isInteractive) {
                delay(15_000L)
                first = true
                continue
            }
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

    /** Closes the app wherever it shows, then stops what it left running. */
    private fun block(pkg: String) {
        if (pkg == pipPackage) {
            dismissPictureInPicture()
            pipPackage = null
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        BlockActivity.launch(this, pkg)
        MeadowNotificationListener.rest(this, pkg)
        scope.launch {
            // Only a process in the background can be ended; give it a moment to get there.
            delay(STOP_AFTER_MS)
            if (!onScreen(pkg)) MeadowNotificationListener.stopInBackground(this@GuardianAccessibilityService, pkg)
        }
    }

    private fun dismissPictureInPicture() {
        val window = runCatching { windows }.getOrNull()?.firstOrNull { it.isInPictureInPictureMode } ?: return
        val dismissed = runCatching { window.root?.performAction(AccessibilityNodeInfo.ACTION_DISMISS) == true }.getOrDefault(false)
        if (!dismissed) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun checkStrict(pkg: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStrictCheck < 700) return
        if (strictJob?.isActive == true) {
            // One read at a time; the screen is read again as soon as this one ends.
            strictRecheck = pkg
            return
        }
        lastStrictCheck = now
        // Reading the screen asks the app for every node, so it happens off the main thread.
        strictJob = scope.launch {
            val blocked = readsAsGuarded(pkg)
            withContext(Dispatchers.Main) {
                if (blocked) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Toast.makeText(this@GuardianAccessibilityService, R.string.strict_mode_blocked, Toast.LENGTH_LONG).show()
                }
                strictJob = null
                strictRecheck?.let {
                    strictRecheck = null
                    if (AppGraph.store.strictMode.value) checkStrict(it, force = true)
                }
            }
        }
    }

    /** Whether the active window shows one of the screens strict mode keeps out of reach. */
    private fun readsAsGuarded(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val text = StringBuilder()
        collectText(root, text, IntArray(1) { 600 })
        val screen = text.toString().lowercase()
        return StrictModeRules.shouldBlock(pkg, screen, appLabel = getString(R.string.app_name).lowercase(), localWords = settingsWords)
    }

    /** Settings' own words for "Force stop", "Uninstall" and so on, in the phone's language. */
    private val settingsWords: List<String> by lazy {
        val res = runCatching { packageManager.getResourcesForApplication(SETTINGS) }.getOrNull() ?: return@lazy emptyList()
        SETTINGS_STRINGS.mapNotNull { name ->
            @Suppress("DiscouragedApi") // Another app's resources can only be found by name.
            val id = res.getIdentifier(name, "string", SETTINGS)
            if (id == 0) null else runCatching { res.getString(id).lowercase().trim() }.getOrNull()?.takeIf { it.length > 2 }
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

        private const val SETTINGS = "com.android.settings"
        private const val STOP_AFTER_MS = 1_500L
        private val SETTINGS_STRINGS = listOf("force_stop", "uninstall_text", "clear_user_data_text", "disable_text")

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

    /** [localWords] are the same dangers in the phone's language, read from the Settings app. */
    fun shouldBlock(pkg: String, screenText: String, appLabel: String, localWords: List<String> = emptyList()): Boolean {
        if (clockKeywords.any { it in screenText } && pkg.contains("settings")) return true
        if (appLabel !in screenText) return false
        return dangerousNearApp.any { it in screenText } || localWords.any { it in screenText }
    }
}

/**
 * Which app each on-screen window belongs to. A window never changes app, so each window's app
 * is looked up once, while it stays open.
 */
internal class WindowPackages {
    private var known: Map<Int, String> = emptyMap()

    /**
     * The apps of [windows], identified by [id]. [lookup] is only asked about windows not seen in
     * the previous call, or whose app it couldn't tell then; windows no longer listed are forgotten.
     */
    fun <W> of(windows: List<W>, id: (W) -> Int, lookup: (W) -> String?): List<String> {
        val next = HashMap<Int, String>(windows.size)
        val packages = windows.mapNotNull { w ->
            val key = id(w)
            (known[key] ?: lookup(w))?.also { next[key] = it }
        }
        known = next
        return packages
    }
}
