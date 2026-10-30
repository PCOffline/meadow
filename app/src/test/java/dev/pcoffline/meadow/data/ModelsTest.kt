package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val day = 24 * 60 * 60_000L

    private fun profile(uses: List<Long>) = UserProfile(
        uid = "me", displayName = "Sam", avatar = Avatar.Default, bio = "", friendCode = "ABC234",
        approverIds = emptyList(), approveeIds = emptyList(), privacy = Privacy(), strictMode = false,
        emergencyUntilMillis = 0L, emergencyPackage = null, emergencyUsesMillis = uses,
    )

    @Test
    fun `emergency allowance only counts the last 30 days`() {
        val now = System.currentTimeMillis()
        assertEquals(Emergency.ALLOWANCE, profile(emptyList()).emergenciesLeft)
        assertEquals(Emergency.ALLOWANCE - 1, profile(listOf(now - day)).emergenciesLeft)
        assertEquals(Emergency.ALLOWANCE, profile(listOf(now - 31 * day)).emergenciesLeft)
        assertEquals(0, profile(List(5) { now - it * day }).emergenciesLeft)
    }

    @Test
    fun `an emergency covers one app or all of them`() {
        val now = 1_000L
        assertTrue(Emergency(2_000L, null).covers("any", now))
        assertTrue(Emergency(2_000L, "a").covers("a", now))
        assertFalse(Emergency(2_000L, "a").covers("b", now))
        assertFalse(Emergency(500L, null).covers("a", now))
    }

    @Test
    fun `invite tokens are found in links and plain text`() {
        val token = "AbCdEfGh12345678"
        assertEquals(token, InviteLinks.tokenFromText(token))
        assertEquals(token, InviteLinks.tokenFromText("Will you be my Bumblebee? https://x.web.app/i/$token"))
        assertEquals(token, InviteLinks.tokenFromText("meadow://invite/$token"))
        assertNull(InviteLinks.tokenFromText("https://x.web.app/i/short"))
        assertNull(InviteLinks.tokenFromText("hello"))
    }

    @Test
    fun `a sign-in link handed back from the browser is unwrapped, and nothing else is taken for one`() {
        val link = "https://x.web.app/signed-in?apiKey=k&oobCode=c0de&mode=signIn&lang=en"
        val wrapped = "meadow://signin?link=" + java.net.URLEncoder.encode(link, "UTF-8")
        assertEquals(link, SignInLinks.fromApp(wrapped))
        assertNull(SignInLinks.fromApp("meadow://invite/AbCdEfGh12345678"))
        assertNull(SignInLinks.fromApp("meadow://signin"))
        assertNull(SignInLinks.fromApp("meadow://signin?link=javascript%3Aalert(1)"))
        assertNull(SignInLinks.fromApp(link))
    }

    @Test
    fun `chats with the same two people share an id`() {
        assertEquals(ChatRepository.chatIdFor("a", "b"), ChatRepository.chatIdFor("b", "a"))
    }

    @Test
    fun `closed chats aren't listed and open as closed`() {
        val open = Chat(
            id = "a_b", participants = listOf("a", "b"), names = mapOf("a" to "Sam", "b" to "Bee"), avatars = emptyMap(),
            lastMessageAtMillis = 1L, lastSenderId = "a", lastReadMillis = emptyMap(),
        )
        val closed = open.copy(id = "a_c", participants = listOf("a", "c"), names = emptyMap(), closed = true)
        assertEquals(listOf(open), ChatRepository.listed(listOf(open, closed)))
        assertEquals(open, ChatRepository.openOrNull(open))
        assertNull(ChatRepository.openOrNull(closed))
    }

    @Test
    fun `day keys sort like dates`() {
        val today = Days.today()
        assertTrue(Days.key(today.minusDays(1)) < Days.key(today))
        assertEquals(today, Days.parse(Days.key(today)))
        assertNull(Days.parse("not a date"))
        assertEquals(java.time.DayOfWeek.MONDAY, Days.weekStart(today).dayOfWeek)
    }

    @Test
    fun `unanswered requests count as expired after half an hour`() {
        val now = 10_000_000_000L
        val minute = 60_000L
        assertEquals(RequestStatus.PENDING, RequestStatus.effective(RequestStatus.PENDING, now - 29 * minute, now))
        assertEquals(RequestStatus.EXPIRED, RequestStatus.effective(RequestStatus.PENDING, now - 30 * minute, now))
        assertEquals(RequestStatus.APPROVED, RequestStatus.effective(RequestStatus.APPROVED, now - 60 * minute, now))
        // Still being written: the server time isn't known yet.
        assertEquals(RequestStatus.PENDING, RequestStatus.effective(RequestStatus.PENDING, 0L, now))
    }
}
