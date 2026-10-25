package dev.pcoffline.meadow

import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.enforcement.BootReceiver
import dev.pcoffline.meadow.enforcement.GuardianService
import dev.pcoffline.meadow.notifications.MeadowMessagingService
import dev.pcoffline.meadow.notifications.Notifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, what the phone itself sends Meadow: pushes (shown only to the account they're
 * addressed to, also after switching accounts on the phone) and the restart broadcast (which
 * brings the guardian back without Meadow being opened).
 */
@RunWith(AndroidJUnit4::class)
class PhoneEventsTest : EndToEndTest() {
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private var sam: EmulatorFriend? = null

    @Before
    fun allowNotifications() {
        // A string, not Manifest.permission.POST_NOTIFICATIONS: that constant is newer than minSdk.
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        // Read to the end, so the grant has happened before the test goes on.
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    @After
    fun closeSam() {
        sam?.close()
    }

    @Test
    fun pushesShowOnlyForTheSignedInAccount() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val bee = FirebaseAuth.getInstance().currentUser!!.uid
            push(to = sam.uid, chatId = "for-sam")
            push(to = bee, chatId = "for-bee")
            waitForNotification("for-bee".hashCode())
            assertFalse("A push for another account was shown", showing("for-sam".hashCode()))

            // Someone else signs in on this phone: pushes still addressed to Bee aren't shown.
            notifications.cancelAll()
            runBlocking {
                AppGraph.auth.signOut()
                AppGraph.auth.signIn(sam.email, EmulatorFriend.PASSWORD)
            }
            push(to = bee, chatId = "still-for-bee")
            push(to = sam.uid, chatId = "now-for-sam")
            waitForNotification("now-for-sam".hashCode())
            assertFalse("A push for the previous account was shown", showing("still-for-bee".hashCode()))
        }
    }

    @Test
    fun theGuardianComesBackAfterARestart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Background sync starts the guardian too; out of the way, only the receiver can.
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.stopService(Intent(app, GuardianService::class.java))
            waitUntil("the guardian stopped") { !showing(Notifier.GUARDIAN_ID) }

            // Only the system's broadcasts count; any app could call the receiver with others.
            receive(Intent("dev.example.NOT_A_RESTART"))
            Thread.sleep(2_000)
            assertFalse("The guardian started for a broadcast it should ignore", showing(Notifier.GUARDIAN_ID))

            receive(Intent(Intent.ACTION_BOOT_COMPLETED))
            waitUntil("the guardian started after the restart") { showing(Notifier.GUARDIAN_ID) }
        }
    }

    /** A push as Firebase Cloud Messaging hands it over: a chat message from Sam. */
    private fun push(to: String, chatId: String) = MeadowMessagingService().onMessageReceived(
        RemoteMessage.Builder("meadow@fcm.googleapis.com")
            .setData(mapOf("type" to "chat", "to" to to, "chatId" to chatId, "title" to "Sam", "body" to "Hello there"))
            .build(),
    )

    /** Delivers [intent] to the restart receiver on the main thread, as the system would. */
    private fun receive(intent: Intent) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync { BootReceiver().onReceive(app, intent) }

    private fun showing(id: Int) = notifications.activeNotifications.any { it.id == id }

    private fun waitForNotification(id: Int) = waitUntil("notification $id shown") { showing(id) }

    private fun waitUntil(what: String, timeout: Long = 15_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!check()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Never saw $what. On screen: ${screenText()}")
            Thread.sleep(250)
        }
    }
}
