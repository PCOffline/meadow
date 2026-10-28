package dev.pcoffline.meadow.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.MainThread
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.BuildConfig
import dev.pcoffline.meadow.data.Browsers
import dev.pcoffline.meadow.data.Sites
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.usage.activityInfo
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
 *
 * For limited websites, it reads the address bar of the browser in front (only in the browsers in
 * [Browsers], and only while a website is limited), compares it with the limited sites and lets
 * go of it: no address is kept or sent. A limited site in front is watched like an app.
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

    /** The limited app shown in picture-in-picture, if any. */
    @Volatile private var pipPackage: String? = null

    /** The limited website (its id) open in the browser in front, if any. */
    @Volatile private var sitePackage: String? = null
    private var siteJob: Job? = null
    private var siteTrailing: Job? = null
    private var siteRecheck = false
    private var lastSiteCheck = 0L
    private var lastLimitedLeft: String? = null
    private var lastLimitedLeftAt = 0L
    private val watchers = HashMap<String, Job>()
    private val windowPackages = WindowPackages()
    private val pipWindows = WindowPackages()
    private val homeGuard = HomeGuard()

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
                if (pkg == currentPackage) checkSite(pkg, force = false)
            }
            else -> Unit // The service config only subscribes to the window events above.
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
            packageManager.activityInfo(ComponentName(pkg, className), PackageManager.MATCH_ALL)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        activityCache.put(key, isActivity)
        return isActivity
    }

    private fun onForeground(pkg: String) {
        if (pkg == currentPackage) return
        currentPackage?.takeIf { AppGraph.engine.limitFor(it) != null }?.let {
            lastLimitedLeft = it
            lastLimitedLeftAt = SystemClock.elapsedRealtime()
        }
        currentPackage = pkg
        AppGraph.engine.setForeground(pkg)
        // A site stays open only while its browser does; back in a browser, its address is read again.
        setSite(null)
        checkSite(pkg, force = true)
        // Picture-in-picture often starts just as its app is left.
        scanWindows()
    }

    private fun onScreen(pkg: String) = pkg == currentPackage || pkg in sidePackages || pkg == pipPackage || pkg == sitePackage

    /** The limited sites' domains; empty when none is limited, and then no address bar is read. */
    private fun limitedDomains(): List<String> = AppGraph.store.limits.value.mapNotNull { Sites.domainOf(it.packageName) }

    private fun addressBarOf(pkg: String): String? =
        Browsers.addressBarOf(pkg, if (BuildConfig.TEST_BROWSER.isEmpty()) emptyMap() else mapOf(BuildConfig.TEST_BROWSER to "url_bar"))

    /**
     * Reads the address bar of [pkg], a browser in front, if any website is limited. At most a
     * couple of times a second, off the main thread; a change while reading is read again after.
     */
    private fun checkSite(pkg: String, force: Boolean) {
        val bar = addressBarOf(pkg) ?: return
        val domains = limitedDomains()
        if (domains.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSiteCheck < SITE_CHECK_MS) {
            // Read once more when the changes settle, so the last address is never missed.
            if (siteTrailing?.isActive != true) {
                siteTrailing = scope.launch {
                    delay(SITE_CHECK_MS)
                    withContext(Dispatchers.Main) { currentPackage?.let { checkSite(it, force = true) } }
                }
            }
            return
        }
        if (siteJob?.isActive == true) {
            siteRecheck = true
            return
        }
        lastSiteCheck = now
        siteJob = scope.launch {
            val read = readAddressBar(bar)
            withContext(Dispatchers.Main) {
                // Gone to another app while reading: what was read no longer applies.
                if (pkg == currentPackage) {
                    when (read) {
                        AddressBar.Unreadable -> Unit
                        is AddressBar.Shows -> setSite(Sites.match(read.text, domains)?.let(Sites::idFor))
                    }
                }
                siteJob = null
                if (siteRecheck) {
                    siteRecheck = false
                    currentPackage?.let { checkSite(it, force = true) }
                }
            }
        }
    }

    /**
     * Only the address bar is read, by its id: never the page. While someone is typing in it,
     * what it shows isn't where they are, so it counts as unreadable and the last site stands.
     */
    private fun readAddressBar(id: String): AddressBar {
        val root = rootInActiveWindow ?: return AddressBar.Unreadable
        val bar = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()?.firstOrNull() ?: return AddressBar.Unreadable
        if (bar.isFocused) return AddressBar.Unreadable
        return AddressBar.Shows(bar.text?.toString().orEmpty())
    }

    /** Measures [site] if it's still the one in front; false once it's been left. */
    @MainThread
    internal fun measureSite(site: String): Boolean {
        if (site != sitePackage) return false
        AppGraph.sites.seen(site)
        return true
    }

    @MainThread
    internal fun setSite(site: String?) {
        if (site == sitePackage) return
        if (sitePackage != null) AppGraph.sites.left()
        sitePackage = site
        updateWatchers()
    }

    /** Finds limited apps in other windows: split screen and picture-in-picture. */
    private fun scanWindows() {
        val all = runCatching { windows }.getOrNull() ?: return
        val pip = ArrayList<AccessibilityWindowInfo>()
        val apps = ArrayList<AccessibilityWindowInfo>()
        for (w in all) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (w.isInPictureInPictureMode) pip += w else apps += w
        }
        // Reading a window's root asks its app, so each window is only asked once.
        val side = HashSet<String>()
        for (pkg in windowPackages.of(apps, { it.id }) { w -> runCatching { w.root?.packageName?.toString() }.getOrNull() }) {
            if (pkg != currentPackage && pkg != packageName && AppGraph.engine.limitFor(pkg) != null) side += pkg
        }
        // Each floating window is attributed once, when it appears.
        pipPackage = pipWindows.of(pip, { it.id }) { w ->
            PipOwner.of(
                windowPackage = runCatching { w.root?.packageName?.toString() }.getOrNull(),
                lastLimitedLeft = lastLimitedLeft,
                leftAgoMs = SystemClock.elapsedRealtime() - lastLimitedLeftAt,
            )
        }.firstOrNull { it != packageName && AppGraph.engine.limitFor(it) != null }
        sidePackages = side
        updateWatchers()
    }

    /** One watcher per limited app on screen; watchers of apps no longer shown stop. */
    @Synchronized
    private fun updateWatchers() {
        val shown = (sidePackages + listOfNotNull(currentPackage, pipPackage, sitePackage))
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
            // A website's time is measured as it's watched, on the main thread where the site in
            // front changes: measured from here, a visit just left could open again and keep counting.
            if (Sites.isSite(pkg) && !withContext(Dispatchers.Main) { measureSite(pkg) }) return
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

    /**
     * Covers the app with the block screen, then stops what it left running. The block screen
     * opens straight over the app: after Home, Android holds back screens that apps in the
     * background open for a few seconds, so going Home first would leave nothing shown.
     */
    internal fun block(pkg: String) {
        // Already covered: opening the screen again, or going Home, would only make it flicker.
        if (BlockActivity.isShowingFor(pkg) && pkg !in sidePackages && pkg != pipPackage) return
        if (pkg == pipPackage) {
            dismissPictureInPicture()
            pipPackage = null
        }
        val since = SystemClock.elapsedRealtime()
        BlockActivity.launch(this, pkg)
        MeadowNotificationListener.rest(this, pkg)
        scope.launch { makeSureBlocked(pkg, since) }
    }

    /**
     * Waits for the block screen to show, opening it again if Android held it back. An app still
     * on screen beside it (the other split-screen half) is left with Home, once, after which the
     * block screen has to show again.
     */
    private suspend fun makeSureBlocked(pkg: String, launchedAt: Long) {
        var since = launchedAt
        var leftHome = false
        repeat(BLOCK_ATTEMPTS) {
            delay(BLOCK_CHECK_MS)
            val shown = BlockActivity.shownSince(pkg, since)
            if (shown && !onScreen(pkg)) {
                // Only a process in the background can be ended, and now it is.
                MeadowNotificationListener.stopInBackground(this, pkg)
                return
            }
            val beside = pkg in sidePackages || pkg == pipPackage
            withContext(Dispatchers.Main) {
                when {
                    beside && !leftHome && homeGuard.allow(pkg, SystemClock.elapsedRealtime()) -> {
                        leftHome = true
                        since = SystemClock.elapsedRealtime()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    !shown -> BlockActivity.launch(this@GuardianAccessibilityService, pkg)
                    else -> Unit // Shown; the app's own window events haven't caught up yet.
                }
            }
        }
    }

    private fun dismissPictureInPicture() {
        val window = runCatching { windows }.getOrNull()?.firstOrNull { it.isInPictureInPictureMode } ?: return
        val dismissed = runCatching { window.root?.performAction(AccessibilityNodeInfo.ACTION_DISMISS) == true }.getOrDefault(false)
        if (!dismissed && homeGuard.allow(pipPackage ?: return, SystemClock.elapsedRealtime())) performGlobalAction(GLOBAL_ACTION_HOME)
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
        // Long enough to outlast the few seconds Android holds screens back after Home.
        private const val BLOCK_CHECK_MS = 1_000L
        private const val BLOCK_ATTEMPTS = 10
        private const val SITE_CHECK_MS = 500L
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

/** What a browser's address bar shows: an address (or search) to compare, or nothing readable. */
internal sealed interface AddressBar {
    data object Unreadable : AddressBar
    data class Shows(val text: String) : AddressBar
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

/**
 * Which app a picture-in-picture window belongs to. Its own [windowPackage] when Android tells;
 * otherwise the limited app left a moment before it appeared ([leftAgoMs]), since picture-in-picture
 * starts as its app is left. Any other floating window, such as another app's video, isn't
 * blamed on a resting app, or the block screen would keep sending the phone Home.
 */
internal object PipOwner {
    const val JUST_LEFT_MS = 3_000L

    fun of(windowPackage: String?, lastLimitedLeft: String?, leftAgoMs: Long): String? =
        windowPackage ?: lastLimitedLeft?.takeIf { leftAgoMs in 0..JUST_LEFT_MS }
}

/**
 * Lets the phone be sent Home for an app at most once in a while. Home is the last resort for an
 * app left beside the block screen; repeating it would trap the phone between Home and the block
 * screen if Android kept reporting the app as shown.
 */
internal class HomeGuard(private val intervalMs: Long = INTERVAL_MS) {
    private val last = HashMap<String, Long>()

    /** Whether Home may be used for [pkg] at [now] (elapsed realtime); if so, counts it. */
    @Synchronized
    fun allow(pkg: String, now: Long): Boolean {
        val previous = last[pkg]
        if (previous != null && now - previous < intervalMs) return false
        last[pkg] = now
        return true
    }

    companion object {
        const val INTERVAL_MS = 30_000L
    }
}
