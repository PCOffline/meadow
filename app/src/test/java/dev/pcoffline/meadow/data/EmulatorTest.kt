package dev.pcoffline.meadow.data

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dev.pcoffline.meadow.ui.legal.LegalDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * A test against the Firebase emulators: Auth, Firestore and Cloud Functions, with the real
 * security rules and functions. It is skipped unless Gradle runs with
 * `-Pmeadow.unitEmulatorHost`, as `just test-app` and CI do, inside `firebase emulators:exec`.
 *
 * Two test JVMs run side by side against the same emulators. So each test makes its own
 * accounts, and no test clears what another wrote.
 */
abstract class EmulatorTest {
    protected val app: Application = ApplicationProvider.getApplicationContext()
    private val host: String = System.getProperty("meadow.unitEmulatorHost").orEmpty()
    protected lateinit var backend: Backend
    protected lateinit var store: LocalStore

    @Before
    fun connectToEmulators() {
        assumeTrue("Run with -Pmeadow.unitEmulatorHost to test against the Firebase emulators", host.isNotEmpty())
        Emulators.connect(app, host)
        backend = Backend()
        backend.auth.signOut()
        store = LocalStore(app)
    }

    @After
    fun signOutOfEmulators() {
        if (host.isNotEmpty()) FirebaseAuth.getInstance().signOut()
    }

    /**
     * Runs [block], which talks to the emulators, on a background thread, and turns the main
     * thread's message loop until it ends. Firebase hands some of its work to the main thread, so
     * a test that blocked that thread while it waited would wait forever.
     */
    protected fun <T> onBackend(block: suspend () -> T): T {
        // Already off the main thread, inside another onBackend: nothing to wait for there.
        if (Looper.myLooper() != Looper.getMainLooper()) return runBlocking { block() }
        val result = CoroutineScope(Dispatchers.IO).async { block() }
        val until = System.currentTimeMillis() + TIMEOUT_MS
        while (!result.isCompleted) {
            check(System.currentTimeMillis() < until) { "The emulators didn't answer within ${TIMEOUT_MS / 1000} seconds" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return runBlocking { result.await() }
    }

    /** An address that no other test uses. */
    protected fun newEmail(): String = "test-${UUID.randomUUID()}@example.com"

    /** A new account, started without an email, with a garden named [name]. Returns its uid. */
    protected fun newGarden(name: String): String = onBackend {
        AuthRepository(backend, store).startWithoutEmail(name, "🌼", LegalDoc.CURRENT_VERSION)
        backend.requireUid()
    }

    /** Someone with a garden and a password, as other people find them. */
    protected data class Person(val uid: String, val email: String, val friendCode: String)

    /** A new person with a garden, signed in. */
    protected fun person(name: String): Person = onBackend {
        backend.auth.signOut()
        val email = newEmail()
        val auth = AuthRepository(backend, store)
        auth.signUp(email, PASSWORD)
        auth.createProfile(name, "🌼", LegalDoc.CURRENT_VERSION)
        val uid = backend.requireUid()
        Person(uid, email, account(uid).getString("friendCode")!!)
    }

    protected fun signInAs(person: Person) = onBackend {
        backend.auth.signOut()
        backend.auth.signInWithEmailAndPassword(person.email, PASSWORD).await()
    }

    /** [uid]'s private account document. Only its owner can read it. */
    protected suspend fun account(uid: String): DocumentSnapshot = backend.account(uid).get().await()

    /** The first value of this flow that matches [predicate]; fails when none comes in time. */
    protected suspend fun <T> Flow<T>.firstWhere(predicate: (T) -> Boolean): T = withTimeout(WAIT_MS) { first(predicate) }

    /** [bee] becomes [owner]'s Bumblebee, by the owner's invite and the bee's yes. [bee] stays signed in. */
    protected fun makeBumblebee(owner: Person, bee: Person) {
        val social = SocialRepository(backend)
        signInAs(owner)
        onBackend { social.invite(bee.friendCode) }
        signInAs(bee)
        onBackend {
            val invite = social.incomingInvites(bee.uid).firstWhere { it.isNotEmpty() }.single()
            social.respond(invite.id, accept = true)
        }
    }

    /** The sign-in links that the Auth emulator would have emailed to [email]. */
    protected fun emailedLinks(email: String): List<String> {
        val url = URL("http://$host:9099/emulator/v1/projects/${Emulators.PROJECT}/oobCodes")
        val body = (url.openConnection() as HttpURLConnection).inputStream.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(body).jsonObject["oobCodes"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["email"]?.jsonPrimitive?.content == email && it["requestType"]?.jsonPrimitive?.content == "EMAIL_SIGNIN" }
            .mapNotNull { it["oobLink"]?.jsonPrimitive?.content }
    }

    protected companion object {
        const val PASSWORD = "longenough"
        const val WAIT_MS = 20_000L
        private const val TIMEOUT_MS = 60_000L
    }
}

/** Points Firebase at the emulators, once for each test JVM, as `AppGraph` does for the journeys. */
internal object Emulators {
    const val PROJECT = "demo-meadow"
    private var connected = false

    @Synchronized
    fun connect(app: Application, host: String) {
        if (connected) return
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setProjectId(PROJECT)
                    .setApplicationId("1:1234567890:android:0000000000000000")
                    .setApiKey("AIzaSyDemoKeyForTheFirebaseEmulators000")
                    .setStorageBucket("$PROJECT.appspot.com")
                    .build(),
            )
        }
        FirebaseAuth.getInstance().useEmulator(host, 9099)
        FirebaseFirestore.getInstance().apply {
            useEmulator(host, 8080)
            // Robolectric gives each test new app folders; a cache on disk would outlive its own.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        FirebaseFunctions.getInstance(Backend.FUNCTIONS_REGION).useEmulator(host, 5001)
        FirebaseStorage.getInstance().useEmulator(host, 9199)
        connected = true
    }
}
