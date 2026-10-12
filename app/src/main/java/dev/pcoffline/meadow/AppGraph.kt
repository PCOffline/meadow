package dev.pcoffline.meadow

import android.app.Application
import com.google.firebase.FirebaseApp
import dev.pcoffline.meadow.data.AuthRepository
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.EmergencyRepository
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.ProfileRepository
import dev.pcoffline.meadow.data.RequestRepository
import dev.pcoffline.meadow.data.SocialRepository
import dev.pcoffline.meadow.data.SyncManager
import dev.pcoffline.meadow.data.UsageShareRepository
import dev.pcoffline.meadow.enforcement.LimitEngine
import dev.pcoffline.meadow.enforcement.ProtectionMonitor
import dev.pcoffline.meadow.notifications.Notifier
import dev.pcoffline.meadow.update.Updater
import dev.pcoffline.meadow.usage.AppCatalog
import dev.pcoffline.meadow.usage.UsageHistory
import dev.pcoffline.meadow.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons. Small enough that a DI framework would only add ceremony. */
object AppGraph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** False when the app was built without a google-services.json. */
    var backendReady = false
        private set

    val store by lazy { LocalStore(app) }
    val backend by lazy { Backend() }
    val auth by lazy { AuthRepository(backend) }
    val sync by lazy { SyncManager(backend, auth, store, scope) }
    val requests by lazy { RequestRepository(backend) }
    val limits by lazy { LimitsRepository(backend, store, requests) }
    val social by lazy { SocialRepository(backend, store, requests) }
    val chats by lazy { ChatRepository(backend) }
    val share by lazy { UsageShareRepository(backend) }
    val tracker by lazy { UsageTracker(app) }
    val catalog by lazy { AppCatalog(app) }
    val history by lazy { UsageHistory(app, tracker, catalog) }
    val engine by lazy { LimitEngine(store, tracker, history, catalog) }
    val emergency by lazy { EmergencyRepository(app, backend, store) }
    val profiles by lazy { ProfileRepository(app, backend, store) }
    val notifier by lazy { Notifier(app, store) }
    val updater by lazy { Updater(app) }
    val protection by lazy { ProtectionMonitor(app, tracker, store, share, scope, backendReady) }

    fun init(application: Application) {
        app = application
        backendReady = BuildConfig.HAS_FIREBASE_CONFIG && FirebaseApp.getApps(application).isNotEmpty()
    }
}
