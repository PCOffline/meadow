package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.navigation.ChatRoute
import dev.pcoffline.meadow.ui.navigation.CircleRoute
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import dev.pcoffline.meadow.ui.navigation.FriendRoute
import dev.pcoffline.meadow.ui.navigation.RequestsRoute
import dev.pcoffline.meadow.ui.navigation.SettingsRoute
import dev.pcoffline.meadow.ui.navigation.TroubleshootRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `notification links map to screens`() {
        assertEquals(RequestsRoute, DeepLinks.toRoute(DeepLinks.requests()))
        assertEquals(CircleRoute, DeepLinks.toRoute(DeepLinks.circle()))
        assertEquals(SettingsRoute, DeepLinks.toRoute(DeepLinks.settings()))
        assertEquals(ChatRoute("a_b"), DeepLinks.toRoute(DeepLinks.chat("a_b")))
        assertEquals(FriendRoute("u1"), DeepLinks.toRoute(DeepLinks.friend("u1")))
    }

    @Test
    fun `unknown or empty links go nowhere`() {
        assertNull(DeepLinks.toRoute("somewhere"))
        assertNull(DeepLinks.toRoute("chat/"))
        assertNull(DeepLinks.toRoute("friend/"))
        assertNull(DeepLinks.toRoute(""))
    }

    @Test
    fun `ask links name the app to ask about`() {
        assertEquals("com.app", DeepLinks.askFor(DeepLinks.ask("com.app")))
        assertNull(DeepLinks.askFor("ask/"))
        assertNull(DeepLinks.askFor(DeepLinks.requests()))
        assertEquals(TroubleshootRoute, DeepLinks.toRoute(DeepLinks.troubleshoot()))
    }
}
