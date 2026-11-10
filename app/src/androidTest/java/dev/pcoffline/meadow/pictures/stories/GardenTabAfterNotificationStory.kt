package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.navigation.ChatRoute
import dev.pcoffline.meadow.ui.navigation.GardenRoute
import dev.pcoffline.meadow.ui.navigation.PeopleRoute
import dev.pcoffline.meadow.ui.navigation.StatsRoute
import dev.pcoffline.meadow.ui.openLink
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.switchTab
import org.junit.Test

/** Notifications open People or a chat, and the Garden tab still opens the garden. */
class GardenTabAfterNotificationStory : Story("The Garden tab opens the garden after a notification") {
    private lateinit var nav: NavHostController

    private fun tab(id: Int) = compose.onNode(hasText(compose.activity.getString(id)) and isSelectable())

    private fun link(route: Any) = compose.runOnUiThread { nav.openLink(route) }

    @Test
    fun story() {
        show(firstCaption = "The garden, as Meadow opens.") {
            nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val current = Tab.entries.firstOrNull { tab -> entry?.destination?.hierarchy?.any(tab.matches) == true } ?: Tab.GARDEN
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { MeadowTabBar(current, Badges(requests = 1)) { nav.switchTab(it.route) } },
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                    NavHost(nav, startDestination = GardenRoute) {
                        composable<GardenRoute> { GardenContent(DemoGarden.robin, DemoGarden.garden) }
                        composable<PeopleRoute> { PeopleContent(DemoGarden.robin, DemoGarden.people) }
                        composable<StatsRoute> { }
                        composable<ChatRoute> {
                            // The demo request's card, as in the gallery: the chat's own card reads the request from the backend.
                            ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, onBack = { nav.popBackStack() }, requestCard = { _, _ ->
                                RequestCard(DemoGarden.request, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp), compact = true)
                            })
                        }
                    }
                }
            }
        }
        step("A notification about a request opens People, on the People tab.") { link(PeopleRoute) }
        step("A tap on Garden opens the garden.") { tab(R.string.tab_garden).performClick() }
        step("A notification about a message opens the chat.") { link(ChatRoute(DemoGarden.chat.id)) }
        step("Back goes to People, the chat's tab.") { compose.runOnUiThread { nav.popBackStack() } }
        step("A tap on Garden opens the garden again.") { tab(R.string.tab_garden).performClick() }
    }
}
