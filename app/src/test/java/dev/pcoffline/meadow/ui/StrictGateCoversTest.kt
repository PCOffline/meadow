package dev.pcoffline.meadow.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.navigation.GardenRoute
import dev.pcoffline.meadow.ui.navigation.SettingsRoute
import dev.pcoffline.meadow.ui.navigation.TroubleshootRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The strict gate opens Troubleshoot, so it must step aside for it rather than cover it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StrictGateCoversTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the gate covers every screen but Troubleshoot`() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = GardenRoute) {
                composable<GardenRoute> { Text("Garden") }
                composable<SettingsRoute> { Text("Settings") }
                composable<TroubleshootRoute> { Text("Troubleshoot") }
            }
        }
        assertTrue(compose.runOnIdle { strictGateCovers(nav.currentDestination) })

        compose.runOnIdle { nav.navigate(TroubleshootRoute) }
        assertFalse(compose.runOnIdle { strictGateCovers(nav.currentDestination) })

        compose.runOnIdle { nav.navigate(SettingsRoute) }
        assertTrue(compose.runOnIdle { strictGateCovers(nav.currentDestination) })

        compose.runOnIdle { nav.popBackStack() }
        assertFalse(compose.runOnIdle { strictGateCovers(nav.currentDestination) })
    }

    @Test
    fun `the gate covers the app before it has a screen`() {
        assertTrue(strictGateCovers(null))
    }
}
