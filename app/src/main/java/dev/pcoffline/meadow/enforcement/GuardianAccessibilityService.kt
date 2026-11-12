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
 * Each of those steps asks Android for something heavy (opening a screen, the day's usage log,
 * every notification, every media session, ending a process). So each happens once for a cover,
 * and an app still on screen is looked at again every couple of seconds, however often its
 * windows change (#106).
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
    private var unreadableReads = 0
    private var lastSiteCheck = 0L
    private var lastLimitedLeft: String? = null
    private var lastLimitedLeftAt = 0L
    private val watchers = HashMap<String, Job>()
    private val windowPackages = WindowPackages()
    private val pipWindows = WindowPackages()
    private val homeGuard = HomeGuard()

    /** The cover under way for each resting app: one at a time, however often it's asked for. */
    private val covering = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val coverStarted = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** When each floating window was first seen (elapsed realtime), by its id. */
    private val pipFirstSeen = HashMap<Int, Long>()
    private var pipDismissedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        GuardianService.start(this)
        AppGraph.protection.check()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scanWindowsSoon()
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

    internal fun onForeground(pkg: String) {
        if (pkg == currentPackage) return
        currentPackage?.takeIf { AppGraph.engine.limitFor(it) != null }?.let {
            lastLimitedLeft = it
            lastLimitedLeftAt = SystemClock.elapsedRealtime()
        }
        currentPackage = pkg
        unreadableReads = 0
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
            val read = readBar(bar)
            withContext(Dispatchers.Main) {
                // Gone to another app while reading: what was read no longer applies.
                if (pkg == currentPackage) {
                    when (read) {
                        AddressBar.Unreadable -> readAgainSoon()
                        is AddressBar.Shows -> {
                            unreadableReads = 0
                            setSite(Sites.match(read.text, domains)?.let(Sites::idFor))
                        }
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
     * Just after a browser comes to the front, its window may not be the active one yet, so its
     * address bar can't be found. A browser that changes nothing after that sends no more events,
     * so the bar is read again a few times instead of waiting for one.
     */
    private fun readAgainSoon() {
        if (unreadableReads >= SITE_READ_RETRIES || siteTrailing?.isActive == true) return
        unreadableReads++
        siteTrailing = scope.launch {
            delay(SITE_CHECK_MS)
            withContext(Dispatchers.Main) { currentPackage?.let { checkSite(it, force = true) } }
        }
    }

    /** Reads an address bar by its full view id. Tests give their own. */
    internal var readBar: (String) -> AddressBar = { readAddressBar(it) }

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

    /** The browser in front, if it's one whose address bar Meadow reads. */
    internal fun browserInFront(): String? = currentPackage?.takeIf { addressBarOf(it) != null }

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

    /** Lists the windows on screen. Tests give their own. */
    internal var listWindows: () -> List<AccessibilityWindowInfo>? = { runCatching { windows }.getOrNull() }

    /** How often the windows are listed after they change, at most. Tests give their own. */
    internal var windowScanMs = WINDOW_SCAN_MS
    private var windowsScannedAt = -WINDOW_SCAN_MS
    private var windowScanPending: Job? = null

    /**
     * Looks at the windows after they changed, at most once in [windowScanMs]. Windows change
     * several times a second while a video plays, and listing them asks Android for every window
     * on screen (#106). A change in between is looked at when the time is up, so the last one is
     * never missed.
     */
    @MainThread
    private fun scanWindowsSoon() {
        val wait = windowsScannedAt + windowScanMs - SystemClock.elapsedRealtime()
        if (wait <= 0) return scanWindows()
        if (windowScanPending?.isActive == true) return
        windowScanPending = scope.launch {
            delay(wait)
            withContext(Dispatchers.Main) { scanWindows() }
        }
    }

    /** Finds limited apps in other windows: split screen and picture-in-picture. */
    private fun scanWindows() {
        windowsScannedAt = SystemClock.elapsedRealtime()
        val all = listWindows() ?: return
        val pip = ArrayList<AccessibilityWindowInfo>()
        val apps = ArrayList<AccessibilityWindowInfo>()
        for (w in all) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (w.isInPictureInPictureMode) pip += w else apps += w
        }
        // Reading a window's root asks its app, so each window is only asked once.
        val side = HashSet<String>()
        for (pkg in windowPackages.of(apps, { it.id }, SystemClock.elapsedRealtime()) { w -> runCatching { w.root?.packageName?.toString() }.getOrNull() }) {
            if (pkg != currentPackage && pkg != packageName && AppGraph.engine.limitFor(pkg) != null) side += pkg
        }
        // Each floating window is attributed once, by when it appeared. A window that was there
        // before a limited app was left (another app's video call) is never that app's, however
        // long Android can't tell whose it is.
        val now = SystemClock.elapsedRealtime()
        pipFirstSeen.keys.retainAll(pip.mapTo(HashSet()) { it.id })
        pipPackage = pipWindows.of(pip, { it.id }, now) { w ->
            PipOwner.of(
                windowPackage = runCatching { w.root?.packageName?.toString() }.getOrNull(),
                lastLimitedLeft = lastLimitedLeft,
                leftAgoMs = pipFirstSeen.getOrPut(w.id) { now } - lastLimitedLeftAt,
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
                // The watcher stays, and looks again in a while, for as long as the app is on
                // screen. Ending here had every window change start a new watcher at once, each
                // with a fresh read of the usage log and another block (#106).
                delay(BLOCKED_RECHECK_MS)
                continue
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
        // A covered site's time stops with it; it's read again from the address bar if it comes back.
        if (pkg == sitePackage) setSite(null)
        // Already covered: opening the screen again, or going Home, would only make it flicker.
        if (BlockActivity.isShowingFor(pkg) && pkg !in sidePackages && pkg != pipPackage) return
        val since = SystemClock.elapsedRealtime()
        if (pkg == pipPackage) {
            // At most once a second: a window that can't be closed is asked again, not hammered.
            if (since - pipDismissedAt >= PIP_DISMISS_MS) {
                pipDismissedAt = since
                dismissPictureInPicture()
            }
            pipPackage = null
        }
        // One cover at a time for each app. The one under way opens the screen again if Android
        // held it back; asking again would open it over itself, and read every notification and
        // media session once more, as often as the windows change (#106).
        covering[pkg]?.takeIf { it.isActive }?.let { underWay ->
            // Unless its screen has shown and gone: then the app was opened again, and this is a
            // new visit to cover.
            val openedAgain = !BlockActivity.isShowingFor(pkg) && BlockActivity.shownSince(pkg, coverStarted[pkg] ?: since)
            if (!openedAgain) return
            underWay.cancel()
        }
        val browser = if (Sites.isSite(pkg)) browserInFront() else null
        BlockActivity.launch(this, pkg, browser)
        coverStarted[pkg] = since
        covering[pkg] = scope.launch {
            // Hiding its notifications and pausing its media asks the system for every
            // notification and session, so it's done here, off the main thread, once for the cover.
            MeadowNotificationListener.rest(this@GuardianAccessibilityService, pkg)
            // A website's sound plays in its browser, which the rest screen covers along with it.
            browser?.let { MeadowNotificationListener.pauseMedia(this@GuardianAccessibilityService, setOf(it)) }
            makeSureBlocked(pkg, since, browser)
        }
    }

    /**
     * Waits for the block screen to show, opening it again if Android held it back. An app still
     * on screen beside it (the other split-screen half) is left with Home, once, after which the
     * block screen has to show again.
     */
    private suspend fun makeSureBlocked(pkg: String, launchedAt: Long, browser: String?) {
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
                    !shown -> BlockActivity.launch(this@GuardianAccessibilityService, pkg, browser)
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
        // How soon a resting app still on screen is looked at again.
        private const val BLOCKED_RECHECK_MS = 2_000L
        private const val PIP_DISMISS_MS = 1_000L
        private const val WINDOW_SCAN_MS = 1_000L
        private const val SITE_CHECK_MS = 500L
        private const val SITE_READ_RETRIES = 4
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

    /** Windows whose app couldn't be told: how often they were asked, and when to ask next. */
    private var unknown: Map<Int, Pair<Int, Long>> = emptyMap()

    /**
     * The apps of [windows], identified by [id]. [lookup] is only asked about windows not seen in
     * the previous call, or whose app it couldn't tell then; windows no longer listed are forgotten.
     * Asking reaches into the window's app, so a window that stays untold is asked less and less
     * often as time ([now], elapsed realtime) passes: at once, then after [FIRST_WAIT_MS], twice
     * that, and so on up to [LONGEST_WAIT_MS].
     */
    fun <W> of(windows: List<W>, id: (W) -> Int, now: Long = 0L, lookup: (W) -> String?): List<String> {
        val next = HashMap<Int, String>(windows.size)
        val nextUnknown = HashMap<Int, Pair<Int, Long>>()
        val packages = windows.mapNotNull { w ->
            val key = id(w)
            known[key]?.let { return@mapNotNull it.also { pkg -> next[key] = pkg } }
            val missed = unknown[key]
            if (missed != null && now < missed.second) {
                nextUnknown[key] = missed
                return@mapNotNull null
            }
            lookup(w)?.also { next[key] = it } ?: run {
                val asked = (missed?.first ?: 0) + 1
                val wait = (FIRST_WAIT_MS shl (asked - 1).coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(LONGEST_WAIT_MS)
                nextUnknown[key] = asked to now + wait
                null
            }
        }
        known = next
        unknown = nextUnknown
        return packages
    }

    companion object {
        const val FIRST_WAIT_MS = 250L
        const val LONGEST_WAIT_MS = 10_000L
        private const val MAX_DOUBLINGS = 10
    }
}

/**
 * Which app a picture-in-picture window belongs to. Its own [windowPackage] when Android tells;
 * otherwise the limited app left just as it appeared ([leftAgoMs]), since picture-in-picture
 * starts as its app is left. Any other floating window, such as another app's video call that was
 * there all along, isn't blamed on a resting app, or the block screen would keep opening over it
 * and sending the phone Home.
 */
internal object PipOwner {
    const val JUST_LEFT_MS = 3_000L

    /** A window can be reported a moment before its app is reported as left. */
    const val JUST_BEFORE_MS = 1_000L

    /** [leftAgoMs] is how long after [lastLimitedLeft] was left the window appeared. */
    fun of(windowPackage: String?, lastLimitedLeft: String?, leftAgoMs: Long): String? =
        windowPackage ?: lastLimitedLeft?.takeIf { leftAgoMs in -JUST_BEFORE_MS..JUST_LEFT_MS }
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
