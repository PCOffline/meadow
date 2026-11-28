package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

/** Documents as the backend, older apps or a damaged write left them: whatever is missing or wrong reads as a safe default. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FirestoreReadsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val at = Timestamp(Date(1_700_000_000_000L))
    private val atMs = 1_700_000_000_000L

    private fun doc(fields: Map<String, Any?>, id: String = "x") = LocalDocs.doc(app, fields, id)

    @Test
    fun `an empty profile reads as a friend with a daisy, sharing nothing`() {
        val profile = doc(emptyMap(), id = "uid1").toProfile()
        assertEquals("uid1", profile.uid)
        assertEquals("Friend", profile.displayName)
        assertEquals(Avatar.Default, profile.avatar)
        assertEquals("", profile.bio)
        assertEquals("", profile.friendCode)
        assertEquals(Privacy(), profile.privacy)
        assertFalse(profile.strictMode)
        assertEquals(0L, profile.emergencyUntilMillis)
        assertTrue(profile.approverIds.isEmpty())
    }

    @Test
    fun `my own profile reads its private parts from my account, or from the profile before it moved`() {
        val profile = doc(mapOf("displayName" to "Robin", "flower" to "🌷", "avatarColor" to "sky", "photoUrl" to "https://p", "bio" to "Hi"))
        val account = doc(
            mapOf(
                "approverIds" to listOf("bee", 7), "approveeIds" to listOf("gardener"),
                "pendingRemovals" to mapOf("bee" to at, "odd" to "not a time"),
                "friendCode" to "CODE1234", "strictMode" to true, "privacy" to mapOf("shareTime" to true),
                "emergencyUntil" to at, "emergencyPackage" to "com.maps", "emergencyUses" to listOf(at, "junk"),
                "theme" to "dusk", "darkMode" to "dark", "acceptedTerms" to mapOf("version" to 3L),
            ),
        )
        val mine = profile.toProfile(account)
        assertEquals(Avatar("🌷", "sky", "https://p"), mine.avatar)
        assertEquals(listOf("bee"), mine.approverIds)
        assertEquals(mapOf("bee" to atMs), mine.pendingRemovals)
        assertEquals("CODE1234", mine.friendCode)
        assertTrue(mine.strictMode)
        assertTrue(mine.privacy.shareTime)
        assertEquals(atMs, mine.emergencyUntilMillis)
        assertEquals("com.maps", mine.emergencyPackage)
        assertEquals(listOf(atMs), mine.emergencyUsesMillis)

        val acc = account.toAccount()
        assertEquals("dusk", acc.theme)
        assertEquals("dark", acc.darkMode)
        assertEquals(3, acc.acceptedTermsVersion)

        // Before the move, a profile kept its own friend code and strict mode.
        val old = doc(mapOf("displayName" to "Robin", "friendCode" to "OLD12345", "strictMode" to true))
        val moved = old.toProfile(doc(mapOf("approverIds" to listOf("bee"))))
        assertEquals("OLD12345", moved.friendCode)
        assertTrue(moved.strictMode)
        // Someone else's profile shows none of it.
        assertEquals("", old.toProfile().friendCode)
    }

    @Test
    fun `an empty account is nobody's, with nothing shared and no terms accepted`() {
        val acc = doc(emptyMap()).toAccount()
        assertEquals(Account(), acc.copy(privacy = Privacy()))
        assertEquals(0, acc.acceptedTermsVersion)
    }

    @Test
    fun `a limit, a grant and a request without their fields read as empty ones`() {
        val limit = doc(emptyMap(), id = "com.pebbles").toLimit()
        assertEquals("com.pebbles", limit.packageName)
        assertEquals("com.pebbles", limit.appName)
        assertEquals(0, limit.dailyMinutes)
        assertNull(limit.schedule)

        val grant = doc(emptyMap(), id = "g1").toGrant()
        assertEquals(Grant("g1", "", 0, ""), grant)

        val request = doc(emptyMap(), id = "r1").toRequest()
        assertEquals("r1", request.id)
        assertEquals("", request.requesterId)
        assertEquals("Friend", request.requesterName)
        assertEquals(RequestType.EXTRA_TIME, request.type)
        assertEquals("", request.message)
        assertEquals("", request.dayKey)
        assertEquals("🌼", request.requesterFlower)
        assertNull(request.usedMinutes)
        assertNull(request.allowedMinutes)

        val inbox = doc(mapOf("requestId" to "r2", "usedMinutes" to 45L, "allowedMinutes" to 60L, "type" to "remove_limit"), id = "copy").toRequest()
        assertEquals("r2", inbox.id)
        assertEquals(45, inbox.usedMinutes)
        assertEquals(60, inbox.allowedMinutes)
        assertEquals(RequestType.REMOVE_LIMIT, inbox.type)
    }

    @Test
    fun `a change request reads the limit as it was, under its own app, and an older one has none`() {
        val change = doc(
            mapOf(
                "type" to "change_limit", "packageName" to "com.insta", "appName" to "Instagram", "dailyMinutes" to 45L,
                "previous" to mapOf("dailyMinutes" to 30L, "weeklyMinutes" to 180L, "caps" to listOf(mapOf("days" to listOf(6L), "from" to 600L, "to" to 960L, "minutes" to 20L))),
            ),
        ).toRequest()
        assertEquals(AppLimit("com.insta", "Instagram", 30, 180, caps = listOf(TimeCap(listOf(6), 600, 960, 20))), change.previous)
        assertEquals(AppLimit("com.insta", "Instagram", 45, 0), change.proposed)
        assertNull(doc(mapOf("type" to "change_limit", "packageName" to "com.insta", "previous" to null)).toRequest().previous)
        assertNull(doc(mapOf("type" to "change_limit", "packageName" to "com.insta")).toRequest().previous)
        assertNull(doc(mapOf("type" to "change_limit", "previous" to mapOf("dailyMinutes" to 30L))).toRequest().previous)
    }

    @Test
    fun `an invite reads its status, and anything else is still waiting`() {
        val empty = doc(emptyMap(), id = "i1").toInvite()
        assertEquals(Invite("i1", "", "Friend", "🌼", "", "Friend", InviteStatus.PENDING, 0L, offer = false), empty)
        for ((wire, status) in listOf("accepted" to InviteStatus.ACCEPTED, "declined" to InviteStatus.DECLINED, "cancelled" to InviteStatus.CANCELLED, "pending" to InviteStatus.PENDING)) {
            assertEquals(status, doc(mapOf("status" to wire, "offer" to true)).toInvite().status)
        }
    }

    @Test
    fun `a chat keeps only the names and pictures it can read`() {
        val chat = doc(
            mapOf(
                "participants" to listOf("a", "b"),
                "names" to mapOf("a" to "Robin", "b" to 3),
                "avatars" to mapOf("a" to mapOf("flower" to "🌷", "color" to "sky", "photo" to "https://p"), "b" to mapOf<String, Any>(), "c" to "junk"),
                "lastRead" to mapOf("a" to at),
                "closed" to true,
            ),
            id = "a_b",
        ).toChat()
        assertEquals(mapOf("a" to "Robin"), chat.names)
        assertEquals(Avatar("🌷", "sky", "https://p"), chat.avatars["a"])
        assertEquals(Avatar.Default, chat.avatars["b"])
        assertFalse("c" in chat.avatars)
        assertEquals(mapOf("a" to atMs), chat.lastReadMillis)
        assertTrue(chat.closed)

        val empty = doc(emptyMap()).toChat()
        assertTrue(empty.avatars.isEmpty() && empty.names.isEmpty() && empty.participants.isEmpty())
        assertFalse(empty.closed)
    }

    @Test
    fun `a message is what it says it is, and a card this version can't read is words`() {
        fun kind(fields: Map<String, Any?>) = doc(fields).toMessage().kind
        assertEquals(MessageKind.REQUEST, kind(mapOf("kind" to "request")))
        assertEquals(MessageKind.SYSTEM, kind(mapOf("kind" to "system")))
        assertEquals(MessageKind.IMAGE, kind(mapOf("kind" to "image")))
        assertEquals(MessageKind.TEXT, kind(mapOf("kind" to "sticker")))
        assertEquals(MessageKind.TEXT, kind(emptyMap()))
        val card = mapOf("packageName" to "com.pebbles", "dailyMinutes" to 30L)
        assertEquals(MessageKind.LIMIT, kind(mapOf("kind" to "limit", "mode" to "shared", "limit" to card)))
        assertEquals(MessageKind.TEXT, kind(mapOf("kind" to "limit", "mode" to "someday", "limit" to card)))
        assertEquals(MessageKind.TEXT, kind(mapOf("kind" to "limit", "mode" to "shared", "limit" to "junk")))

        val empty = doc(emptyMap()).toMessage()
        assertEquals("", empty.senderId)
        assertEquals("", empty.text)
        assertNull(empty.picture)

        val picture = doc(mapOf("kind" to "image", "path" to "chats/a_b/a/p.jpg")).toMessage().picture!!
        assertEquals(1 to 1, picture.width to picture.height)
        assertFalse(picture.gif)
        val sized = doc(mapOf("kind" to "image", "path" to "p.gif", "width" to 0L, "height" to 300L, "gif" to true)).toMessage().picture!!
        assertEquals(1 to 300, sized.width to sized.height)
        assertTrue(sized.gif)
    }

    @Test
    fun `a limit on a card needs at least its app`() {
        assertNull(limitOrNull("junk"))
        assertNull(limitOrNull(mapOf("appName" to "Pebbles")))
        val bare = limitOrNull(mapOf("packageName" to "com.pebbles", "dailyMinutes" to "lots"))!!
        assertEquals("com.pebbles", bare.appName)
        assertEquals(0, bare.dailyMinutes)
    }

    @Test
    fun `shared apps skip entries without an app, and progress without a comparison has none`() {
        val apps = doc(
            mapOf("apps" to listOf(mapOf("packageName" to "com.a", "appName" to "A", "minutes" to 5L), mapOf("appName" to "No app"), "junk", mapOf("packageName" to "com.b"))),
        ).toSharedApps()
        assertEquals(listOf(SharedAppUsage("com.a", "A", 5), SharedAppUsage("com.b", "", 0)), apps)
        assertTrue(doc(emptyMap()).toSharedApps().isEmpty())

        assertNull(doc(emptyMap()).toProgress().weekChange)
        assertEquals(-20, doc(mapOf("weekChange" to -20L)).toProgress().weekChange)
    }

    @Test
    fun `caps and days' own limits that don't make sense are left out`() {
        assertEquals(
            listOf(TimeCap(listOf(1, 2), 60, 120, 30)),
            TimeCap.listFromFirestore(
                listOf(
                    mapOf("days" to listOf(2L, 1L, 2L), "from" to 60L, "to" to 120L, "minutes" to 30L),
                    mapOf("days" to "monday", "from" to 60L, "to" to 120L, "minutes" to 30L),
                    mapOf("days" to listOf(1L), "to" to 120L, "minutes" to 30L),
                    mapOf("days" to listOf(1L), "from" to 60L, "minutes" to 30L),
                    mapOf("days" to listOf(1L), "from" to 60L, "to" to 120L),
                    // A window that ends before it starts can't be a cap.
                    mapOf("days" to listOf(1L), "from" to 120L, "to" to 60L, "minutes" to 30L),
                    "junk",
                ),
            ),
        )
        assertTrue(TimeCap.listFromFirestore("junk").isEmpty())
        assertEquals(
            mapOf(6 to 120, 7 to 0),
            DayMinutes.fromFirestore(mapOf("6" to 120L, "7" to 0L, "8" to 30L, "x" to 30L, "1" to 5000L, "2" to "lots")),
        )
    }
}
