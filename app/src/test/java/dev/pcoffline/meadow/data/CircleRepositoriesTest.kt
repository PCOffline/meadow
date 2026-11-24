package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Bumblebees and requests, between two people, against the emulators: inviting by friend code
 * and by link, strict mode, and asking for more time and the answer.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CircleRepositoriesTest : EmulatorTest() {
    private val social by lazy { SocialRepository(backend) }
    private val requests by lazy { RequestRepository(backend) { null } }

    @Suppress("UNCHECKED_CAST")
    private suspend fun approvers(uid: String) = account(uid).get("approverIds") as List<String>

    @Test
    fun `an invite by friend code is seen by both, and a yes makes a Bumblebee`() {
        val robin = person("Robin")
        val bee = person("Bee")
        signInAs(robin)
        onBackend {
            // Typed in lower case with a space: the code is still found.
            social.invite(" ${bee.friendCode.lowercase()} ")
            val sent = social.outgoingInvites(robin.uid).firstWhere { it.isNotEmpty() }.single()
            assertEquals("Bee", sent.toName)
        }

        signInAs(bee)
        onBackend {
            val invite = social.incomingInvites(bee.uid).firstWhere { it.isNotEmpty() }.single()
            assertEquals("Robin", invite.fromName)
            social.respond(invite.id, accept = true)
            social.incomingInvites(bee.uid).firstWhere { it.isEmpty() }
        }

        signInAs(robin)
        onBackend {
            assertEquals(listOf(bee.uid), approvers(robin.uid))
            // A Bumblebee's name card can be read now; one that doesn't exist is left out.
            assertEquals(listOf("Bee"), social.profiles(listOf(bee.uid, "nobody")).firstWhere { it.isNotEmpty() }.map { it.displayName })
        }
    }

    @Test
    fun `a cancelled invite is gone for both`() {
        val robin = person("Robin")
        val bee = person("Bee")
        signInAs(robin)
        onBackend {
            social.invite(bee.friendCode)
            val invite = social.outgoingInvites(robin.uid).firstWhere { it.isNotEmpty() }.single()
            social.cancelInvite(invite.id)
            social.outgoingInvites(robin.uid).firstWhere { it.isEmpty() }
        }
        signInAs(bee)
        onBackend { assertTrue(social.incomingInvites(bee.uid).first().isEmpty()) }
    }

    @Test
    fun `an invite link says whose it is, and works once`() {
        val robin = person("Robin")
        val bee = person("Bee")
        signInAs(robin)
        val link = onBackend { social.createInviteLink() }
        assertTrue(link.expiresAtMillis > System.currentTimeMillis())
        onBackend { assertEquals(InviteLinkStatus.OWN, social.previewInviteLink(link.token).status) }

        signInAs(bee)
        onBackend {
            val preview = social.previewInviteLink(link.token)
            assertEquals(InviteLinkStatus.OK, preview.status)
            assertEquals("Robin", preview.ownerName)
            social.acceptInviteLink(link.token)
            assertTrue(social.previewInviteLink(link.token).status in setOf(InviteLinkStatus.USED, InviteLinkStatus.ALREADY))
        }
        signInAs(robin)
        onBackend { assertEquals(listOf(bee.uid), approvers(robin.uid)) }
    }

    @Test
    fun `strict mode turns on and off, and needs the phone set up first`() {
        val robin = person("Robin")
        val error = assertThrows(FriendlyException::class.java) {
            onBackend { SocialRepository(backend) { false }.setStrictMode(true) }
        }
        assertEquals(R.string.error_strict_needs_setup, error.messageRes)

        onBackend {
            social.setStrictMode(true)
            assertEquals(true, account(robin.uid).getBoolean("strictMode"))
            social.setStrictMode(false)
            assertEquals(false, account(robin.uid).getBoolean("strictMode"))
        }
    }

    @Test
    fun `asking a Bumblebee for more time, and their answer`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)

        signInAs(robin)
        val id = onBackend {
            LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0))
            val id = requests.create(RequestType.EXTRA_TIME, " Finishing a message ", APP, "Pebbles", minutes = 30)
            val mine = requests.observe(id).firstWhere { it != null }!!
            assertEquals(RequestStatus.PENDING, mine.status)
            assertEquals("Finishing a message", mine.message)
            id
        }

        signInAs(bee)
        onBackend {
            val asked = requests.incoming(bee.uid).firstWhere { list -> list.any { it.id == id } }.single { it.id == id }
            assertEquals("Robin", asked.requesterName)
            assertEquals(asked, requests.observeInbox(bee.uid, id).firstWhere { it != null })
            requests.decide(id, approve = true, note = " Enjoy ", minutes = 10)
            // The Bumblebee's copy says what they gave.
            assertEquals(10, requests.observeInbox(bee.uid, id).firstWhere { it?.status == RequestStatus.APPROVED }!!.grantedMinutes)
        }

        signInAs(robin)
        onBackend {
            val answered = requests.observe(id).firstWhere { it?.status == RequestStatus.APPROVED }!!
            assertEquals("Bee", answered.decidedByName)
            assertEquals("Enjoy", answered.decisionNote)
            // Less than asked for: the request now holds what was given.
            assertEquals(10, answered.minutes)
        }
    }

    @Test
    fun `a request can be withdrawn, and one for the same app at a time`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)

        signInAs(robin)
        onBackend {
            LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0))
            val id = requests.create(RequestType.EXTRA_TIME, packageName = APP, appName = "Pebbles", minutes = 15)
            assertThrows(Backend.CallFailed::class.java) {
                onBackend { requests.create(RequestType.EXTRA_TIME, packageName = APP, appName = "Pebbles", minutes = 15) }
            }
            requests.cancel(id)
            assertEquals(RequestStatus.CANCELLED, requests.observe(id).firstWhere { it?.status == RequestStatus.CANCELLED }!!.status)
        }
    }

    @Test
    fun `with a Bumblebee, a looser limit waits for a yes, and a new limit can withdraw it`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)

        signInAs(robin)
        onBackend {
            val limits = LimitsRepository(backend, store, requests)
            val strict = AppLimit(APP, "Pebbles", 30, 0)
            limits.save(strict)
            store.setLimits(listOf(strict))
            store.setApproverCount(1)

            assertEquals(ChangeOutcome.REQUESTED, limits.save(strict.copy(dailyMinutes = 60)))
            val asked = requests.outgoing(robin.uid, fresh = true).firstWhere { list -> list.any { it.type == RequestType.CHANGE_LIMIT } }
            assertEquals(RequestStatus.PENDING, asked.single { it.type == RequestType.CHANGE_LIMIT }.status)

            requests.withdrawChanges(APP)
            requests.outgoing(robin.uid).firstWhere { list -> list.none { it.type == RequestType.CHANGE_LIMIT && it.status == RequestStatus.PENDING } }
            // Stricter applies at once, Bumblebee or not.
            assertEquals(ChangeOutcome.APPLIED, limits.save(strict.copy(dailyMinutes = 20)))
        }
    }

    @Test
    fun `with a Bumblebee, a stricter cap goes through the backend, and removing a limit asks first`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)

        signInAs(robin)
        onBackend {
            val limits = LimitsRepository(backend, store, requests)
            val limit = AppLimit(APP, "Pebbles", 30, 0)
            limits.save(limit)
            store.setLimits(listOf(limit))
            store.setApproverCount(1)

            // Rules can't compare caps, so the backend saves this stricter one.
            val capped = limit.copy(caps = listOf(TimeCap(listOf(1, 2, 3, 4, 5), from = 20 * 60, to = 23 * 60, minutes = 10)))
            assertEquals(ChangeOutcome.APPLIED, limits.save(capped))
            backend.user(robin.uid).collection("limits").document(APP).snapshots().firstWhere { (it.get("caps") as? List<*>)?.size == 1 }

            assertEquals(ChangeOutcome.REQUESTED, limits.remove(capped, "Not needed"))
            val asked = requests.outgoing(robin.uid, fresh = true).firstWhere { list -> list.any { it.type == RequestType.REMOVE_LIMIT } }
            assertEquals("Not needed", asked.single { it.type == RequestType.REMOVE_LIMIT }.message)
        }
    }

    @Test
    fun `alone, removing a limit deletes it, and a limit that holds nothing back is refused`() {
        val robin = person("Robin")
        onBackend {
            val limits = LimitsRepository(backend, store, requests)
            val limit = AppLimit(APP, "Pebbles", 30, 0)
            limits.save(limit)
            assertEquals(ChangeOutcome.APPLIED, limits.remove(limit))
            assertTrue(!backend.user(robin.uid).collection("limits").document(APP).get().await().exists())

            val nothing = runCatching { limits.save(AppLimit(APP, "Pebbles", 0, 0)) }.exceptionOrNull()
            assertEquals(R.string.error_set_a_limit, (nothing as FriendlyException).messageRes)
        }
    }

    @Test
    fun `with a Bumblebee, a limit's plant changes at once, also while a looser limit waits for a yes`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)

        signInAs(robin)
        onBackend {
            val limits = LimitsRepository(backend, store, requests)
            val doc = backend.user(robin.uid).collection("limits").document(APP)
            suspend fun saved() = doc.get().await().toLimit()
            val strict = AppLimit(APP, "Pebbles", 30, 0)
            limits.save(strict)
            store.setLimits(listOf(strict))
            store.setApproverCount(1)

            assertEquals(ChangeOutcome.APPLIED, limits.save(strict.copy(plant = 3)))
            assertEquals(strict.copy(plant = 3), saved())
            store.setLimits(listOf(strict.copy(plant = 3)))

            // The time waits for a yes; the plant doesn't.
            assertEquals(ChangeOutcome.REQUESTED, limits.save(strict.copy(dailyMinutes = 60, plant = 1)))
            assertEquals(strict.copy(plant = 1), saved())
            store.setLimits(listOf(strict.copy(plant = 1)))

            // Hours go through the backend, which keeps the plant; the plant then changes on its own.
            val hours = Schedule.everyDay(TimeWindow(9 * 60, 17 * 60))
            assertEquals(ChangeOutcome.APPLIED, limits.save(strict.copy(schedule = hours)))
            assertEquals(strict.copy(schedule = hours), saved())
        }
    }

    private companion object {
        const val APP = "com.example.pebbles"
    }
}
