package dev.pcoffline.meadow.data

import android.app.Application
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Task
import org.robolectric.Shadows.shadowOf

/**
 * Real Firestore documents with any fields, for testing how the app reads them. They live only
 * in a cache: this Firestore points at a server that isn't there, so nothing is ever sent.
 */
object LocalDocs {
    private var db: FirebaseFirestore? = null
    private var count = 0

    @Synchronized
    private fun db(app: Application): FirebaseFirestore = db ?: run {
        val firebase = FirebaseApp.getApps(app).firstOrNull { it.name == NAME } ?: FirebaseApp.initializeApp(
            app,
            FirebaseOptions.Builder()
                .setProjectId("demo-local-docs")
                .setApplicationId("1:1234567890:android:0000000000000001")
                .setApiKey("AIzaSyDemoKeyForLocalDocumentsOnly00000")
                .build(),
            NAME,
        )
        FirebaseFirestore.getInstance(firebase).apply {
            // Never a real server: an address that discards everything, and no network at all.
            useEmulator("127.0.0.1", 9)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            wait(disableNetwork())
        }.also { db = it }
    }

    /** A document with [fields], as a phone reads it. Its id is [id]. */
    fun doc(app: Application, fields: Map<String, Any?>, id: String = "doc${++count}"): DocumentSnapshot {
        val ref = db(app).collection("local${++count}").document(id)
        ref.set(fields)
        return checkNotNull(wait(ref.get(Source.CACHE)))
    }

    private fun <T> wait(task: Task<T>): T? {
        val until = System.currentTimeMillis() + 10_000
        while (!task.isComplete) {
            check(System.currentTimeMillis() < until) { "The local cache didn't answer" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return task.result
    }

    private const val NAME = "local-docs"
}
