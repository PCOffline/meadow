package dev.pcoffline.meadow.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.EmulatorTest
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.RequestRepository
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.stringList
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Pushes and answers from a notification, for someone signed in, against the emulators. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotificationsSignedInTest : EmulatorTest() {
    private val manager by lazy { app.getSystemService(NotificationManager::class.java) }

    @Before
    fun startTheApp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.backendReady = true
        AppGraph.notifier.createChannels()
        manager.cancelAll()
    }

    private fun push(data: Map<String, String>) =
        MeadowMessagingService().onMessageReceived(RemoteMessage(Bundle().apply { data.forEach { (k, v) -> putString(k, v) } }))

    private fun answer(requestId: String, approve: Boolean): ListenableWorker.Result = onBackend {
        TestListenableWorkerBuilder<AnswerRequestWorker>(app as Context)
            .setInputData(
                workDataOf(
                    RequestActionReceiver.EXTRA_REQUEST to requestId,
                    RequestActionReceiver.EXTRA_APPROVE to approve,
                    RequestActionReceiver.EXTRA_NOTIFICATION to NOTIFICATION,
                ),
            )
            .build()
            .doWork()
    }

    @Test
    fun `a push for the person signed in is shown, and one for someone else isn't`() {
        val robin = person("Robin")
        push(mapOf("to" to "someone-else", "type" to "test", "channel" to "chat", "title" to "Not for Robin"))
        assertEquals(0, shadowOf(manager).allNotifications.size)

        push(mapOf("to" to robin.uid, "type" to "test", "channel" to "chat", "title" to "For Robin"))
        assertEquals("For Robin", shadowOf(manager).allNotifications.single().extras.getCharSequence("android.title").toString())

        // A push with nothing in it shows nothing.
        manager.cancelAll()
        push(emptyMap())
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `a new push id for this phone is kept on the account`() {
        val robin = person("Robin")
        MeadowMessagingService().onRegistered("phone-from-push")
        onBackend {
            val until = System.currentTimeMillis() + WAIT_MS
            while ("phone-from-push" !in account(robin.uid).stringList("pushIds")) {
                check(System.currentTimeMillis() < until) { "The push id never reached the account" }
                delay(100)
            }
        }
    }

    @Test
    fun `a Bumblebee answers from the notification, and the person hears the answer`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        val requests = RequestRepository(backend) { null }
        val id = onBackend {
            LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0))
            requests.create(RequestType.EXTRA_TIME, "Finishing a message", APP, "Pebbles", minutes = 15)
        }

        signInAs(bee)
        assertEquals(ListenableWorker.Result.success(), answer(id, approve = true))
        assertEquals(app.getString(R.string.request_sent_yes), ShadowToast.getTextOfLatestToast())

        signInAs(robin)
        onBackend { assertEquals(RequestStatus.APPROVED, requests.observe(id).firstWhere { it?.status != RequestStatus.PENDING }?.status) }
    }

    @Test
    fun `an answer the backend refuses fails, and says why`() {
        person("Bee")
        assertEquals(ListenableWorker.Result.failure(), answer("no-such-request", approve = false))
        assertTrue(ShadowToast.getTextOfLatestToast().isNotBlank())
    }

    @Test
    fun `an answer without a request does nothing`() {
        val result = onBackend { TestListenableWorkerBuilder<AnswerRequestWorker>(app as Context).build().doWork() }
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private companion object {
        const val APP = "com.example.pebbles"
        const val NOTIFICATION = 42
    }
}
