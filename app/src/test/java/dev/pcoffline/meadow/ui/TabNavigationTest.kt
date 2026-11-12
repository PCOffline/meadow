package dev.pcoffline.meadow.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.navigation.ChatRoute
import dev.pcoffline.meadow.ui.navigation.GardenRoute
import dev.pcoffline.meadow.ui.navigation.PeopleRoute
import dev.pcoffline.meadow.ui.navigation.SettingsRoute
import dev.pcoffline.meadow.ui.navigation.StatsRoute
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The tab bar and notification links, on a navigation graph shaped like the app's. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TabNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private fun showApp() = compose.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = GardenRoute) {
            composable<GardenRoute> { Text("Garden screen") }
            composable<PeopleRoute> { Text("People screen") }
            composable<StatsRoute> { Text("Growth screen") }
            composable<SettingsRoute> { Text("Settings screen") }
            composable<ChatRoute> { Text("Chat ${it.toRoute<ChatRoute>().chatId}") }
        }
    }

    private fun act(action: NavHostController.() -> Unit) {
        compose.runOnIdle { nav.action() }
        compose.waitForIdle()
    }

    private inline fun <reified T : Any> assertOn() =
        compose.runOnIdle { assertTrue("on ${nav.currentDestination?.route}", nav.currentDestination?.hasRoute<T>() == true) }

    @Test
    fun `the garden tab opens the garden after a notification opened People`() {
        showApp()
        act { openLink(PeopleRoute) }
        assertOn<PeopleRoute>()

        act { switchTab(GardenRoute) }

        assertOn<GardenRoute>()
        compose.onNodeWithText("Garden screen").assertIsDisplayed()
    }

    @Test
    fun `a chat from a notification opens on the People tab`() {
        showApp()
        act { openLink(ChatRoute("a_b")) }
        assertOn<ChatRoute>()

        act { popBackStack() }
        assertOn<PeopleRoute>()

        act { switchTab(GardenRoute) }
        assertOn<GardenRoute>()
    }

    @Test
    fun `a tab keeps its screens while another tab is open`() {
        showApp()
        act { switchTab(PeopleRoute) }
        act { navigate(ChatRoute("a_b")) }
        act { switchTab(GardenRoute) }
        assertOn<GardenRoute>()

        act { switchTab(PeopleRoute) }

        assertOn<ChatRoute>()
        act { switchTab(StatsRoute) }
        assertOn<StatsRoute>()
        act { switchTab(GardenRoute) }
        assertOn<GardenRoute>()
    }

    @Test
    fun `settings from a notification opens over the current screen`() {
        showApp()
        act { openLink(SettingsRoute) }
        assertOn<SettingsRoute>()
        act { popBackStack() }
        assertOn<GardenRoute>()
    }
}
