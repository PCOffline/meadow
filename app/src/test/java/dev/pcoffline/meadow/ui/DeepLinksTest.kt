package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.navigation.ChatRoute
import dev.pcoffline.meadow.ui.navigation.CircleRoute
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import dev.pcoffline.meadow.ui.navigation.FriendRoute
import dev.pcoffline.meadow.ui.navigation.RequestsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `notification links map to screens`() {
        assertEquals(RequestsRoute, DeepLinks.toRoute(DeepLinks.requests()))
        assertEquals(CircleRoute, DeepLinks.toRoute(DeepLinks.circle()))
        assertEquals(ChatRoute("a_b"), DeepLinks.toRoute(DeepLinks.chat("a_b")))
        assertEquals(FriendRoute("u1"), DeepLinks.toRoute(DeepLinks.friend("u1")))
    }

    @Test
    fun `unknown or empty links go nowhere`() {
        assertNull(DeepLinks.toRoute("somewhere"))
        assertNull(DeepLinks.toRoute("chat/"))
    }
}
