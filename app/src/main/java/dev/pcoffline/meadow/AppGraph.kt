package dev.pcoffline.meadow

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dev.pcoffline.meadow.data.AuthRepository
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.DayUsage
import dev.pcoffline.meadow.data.EmergencyRepository
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.ProfileRepository
import dev.pcoffline.meadow.data.RequestRepository
import dev.pcoffline.meadow.data.SocialRepository
import dev.pcoffline.meadow.data.SyncManager
import dev.pcoffline.meadow.data.Trace
import dev.pcoffline.meadow.data.UsageShareRepository
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.LimitEngine
import dev.pcoffline.meadow.enforcement.ProtectionMonitor
import dev.pcoffline.meadow.notifications.Notifier
import dev.pcoffline.meadow.update.Updater
import dev.pcoffline.meadow.usage.AppCatalog
import dev.pcoffline.meadow.usage.ProgressMeter
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons. Small enough that a DI framework would only add ceremony. */
object AppGraph {
    lateinit var app: Application
        private set

    /** Work on [scope] that fails is logged; uncaught, it would crash the whole app. */
    private val logFailures = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background work failed", e) }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + logFailures)

    /** False when the app was built without a google-services.json. */
    var backendReady = false
        private set

    val store by lazy { LocalStore(app) }
    val backend by lazy { Backend() }
    val auth by lazy { AuthRepository(backend, store) }
    val sync by lazy { SyncManager(backend, auth, store, scope) }
    val requests by lazy {
        RequestRepository(backend) { pkg ->
            engine.budget(pkg, forceRefresh = true)?.let { b ->
                DayUsage(
                    usedMinutes = (b.usedTodayMs / AppBudget.MINUTE).toInt().coerceIn(0, 1440),
                    allowedMinutes = b.allowedTodayMs?.let { (it / AppBudget.MINUTE).toInt().coerceIn(0, 10080) },
                )
            }
        }
    }
    val limits by lazy { LimitsRepository(backend, store, requests) }
    val social by lazy { SocialRepository(backend) { protection.current().strictReady } }
    val chats by lazy { ChatRepository(backend) }
    val share by lazy { UsageShareRepository(backend) }
    val tracker by lazy { UsageTracker(app) }
    val catalog by lazy { AppCatalog(app) }
    val history by lazy { UsageHistory(app, tracker, catalog) }
    val progress by lazy { ProgressMeter(history, store) }
    val engine by lazy { LimitEngine(store, tracker, history, catalog) }
    val emergency by lazy { EmergencyRepository(app, backend, store) }
    val profiles by lazy { ProfileRepository(app, backend, store, share) { sync } }
    val notifier by lazy { Notifier(app, store) }
    val updater by lazy { Updater(app) }
    val protection by lazy { ProtectionMonitor(app, tracker, store, share, scope, backendReady) }

    fun init(application: Application) {
        app = application
        Connectivity.watch(application)
        val emulatorHost = BuildConfig.EMULATOR_HOST
        if (emulatorHost.isNotEmpty()) useEmulators(application, emulatorHost)
        backendReady = (BuildConfig.HAS_FIREBASE_CONFIG || emulatorHost.isNotEmpty()) && FirebaseApp.getApps(application).isNotEmpty()
        if (Trace.enabled && backendReady) {
            // The SDK's own log of every read, write and listen, under the "Firestore" tag.
            FirebaseFirestore.setLoggingEnabled(true)
            Trace.log {
                "trace on: project=${FirebaseApp.getInstance().options.projectId} region=${Backend.FUNCTIONS_REGION} " +
                    "emulators=${emulatorHost.ifEmpty { "off" }} version=${BuildConfig.VERSION_NAME}"
            }
        }
    }

    private const val TAG = "Meadow"

    /** Points Firebase at the local emulators, for end-to-end tests. Never set in release builds. */
    private fun useEmulators(application: Application, host: String) {
        if (FirebaseApp.getApps(application).isEmpty()) {
            FirebaseApp.initializeApp(
                application,
                FirebaseOptions.Builder()
                    .setProjectId("demo-meadow")
                    .setApplicationId("1:1234567890:android:0000000000000000")
                    // Never used for real requests, but Firebase checks that it looks like an API key.
                    .setApiKey("AIzaSyDemoKeyForTheFirebaseEmulators000")
                    .setStorageBucket("demo-meadow.appspot.com")
                    .build(),
            )
        }
        FirebaseAuth.getInstance().useEmulator(host, 9099)
        FirebaseFirestore.getInstance().useEmulator(host, 8080)
        FirebaseFunctions.getInstance(Backend.FUNCTIONS_REGION).useEmulator(host, 5001)
        FirebaseStorage.getInstance().useEmulator(host, 9199)
    }
}
