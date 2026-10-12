package dev.pcoffline.meadow.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.pcoffline.meadow.ui.components.PetalBurst
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.auth.ProfileSetupScreen
import dev.pcoffline.meadow.ui.chat.ChatListScreen
import dev.pcoffline.meadow.ui.chat.ChatScreen
import dev.pcoffline.meadow.ui.circle.CircleScreen
import dev.pcoffline.meadow.ui.circle.FriendScreen
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.home.GardenScreen
import dev.pcoffline.meadow.ui.limits.LimitEditorScreen
import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import dev.pcoffline.meadow.ui.requests.RequestsScreen
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.setup.BackendMissingScreen
import dev.pcoffline.meadow.ui.setup.SetupScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MeadowRoot(pendingRoute: MutableStateFlow<String?>) {
    if (!AppGraph.backendReady) {
        BackendMissingScreen()
        return
    }
    val state by AppGraph.sync.profile.collectAsStateWithLifecycle()
    when (val s = state) {
        ProfileState.SignedOut -> AuthScreen()
        ProfileState.Loading -> Loading()
        ProfileState.Missing -> ProfileSetupScreen()
        is ProfileState.Ready -> MainScaffold(s.profile, pendingRoute)
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    GARDEN("garden", "Garden", Icons.Outlined.LocalFlorist),
    STATS("stats", "Growth", Icons.Outlined.BarChart),
    REQUESTS("requests", "Requests", Icons.Outlined.Inbox),
    CIRCLE("circle", "Circle", Icons.Outlined.People),
    CHATS("chats", "Chats", Icons.Outlined.ChatBubbleOutline),
}

@Composable
private fun MainScaffold(profile: UserProfile, pendingRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }
    val badges = rememberBadges(profile.uid)
    var celebration by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val celebrate = remember {
        {
            celebration++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(Unit) {
        if (!AppGraph.protection.current().essentialsReady) nav.navigate("setup")
    }

    val route by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        pendingRoute.value = null
        nav.navigate(target) { launchSingleTop = true }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar, LocalCelebrate provides celebrate) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Tab.entries.forEach { tab ->
                            val count = when (tab) {
                                Tab.REQUESTS -> badges.requests
                                Tab.CIRCLE -> badges.invites
                                Tab.CHATS -> badges.chats
                                else -> 0
                            }
                            val selected = currentRoute == tab.route
                            val iconScale by animateFloatAsState(
                                if (selected) 1.18f else 1f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                label = "tabIcon",
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(tab.route) },
                                icon = {
                                    BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                                        )
                                    }
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(
                    nav,
                    startDestination = Tab.GARDEN.route,
                    enterTransition = { fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 28 } },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(260)) },
                    popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 28 } },
                ) {
                    composable("setup") { SetupScreen(onDone = { nav.popBackStack() }) }
                    composable(Tab.GARDEN.route) { GardenScreen(profile, nav) }
                    composable(Tab.STATS.route) { dev.pcoffline.meadow.ui.stats.StatsScreen(nav) }
                    composable(Tab.REQUESTS.route) { RequestsScreen(profile, nav) }
                    composable(Tab.CIRCLE.route) { CircleScreen(profile, nav) }
                    composable(Tab.CHATS.route) { ChatListScreen(profile, nav) }
                    composable("settings") { SettingsScreen(profile, nav) }
                    composable("pick-app") { AppPickerScreen(nav) }
                    composable(
                        "limit/{pkg}",
                        arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
                    ) { entry ->
                        LimitEditorScreen(entry.arguments?.getString("pkg").orEmpty(), nav)
                    }
                    composable(
                        "chat/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
                    ) { entry ->
                        ChatScreen(profile, entry.arguments?.getString("chatId").orEmpty(), nav)
                    }
                    composable(
                        "friend/{uid}",
                        arguments = listOf(navArgument("uid") { type = NavType.StringType }),
                    ) { entry ->
                        FriendScreen(profile, entry.arguments?.getString("uid").orEmpty(), nav)
                    }
                }
                PetalBurst(celebration, Modifier.fillMaxSize())
            }
        }
    }
}

fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class Badges(val requests: Int, val invites: Int, val chats: Int)

@Composable
private fun rememberBadges(uid: String): Badges {
    val incoming by remember(uid) { AppGraph.requests.incoming(uid) }.collectAsStateWithLifecycle(emptyList())
    val invites by remember(uid) { AppGraph.social.incomingInvites(uid) }.collectAsStateWithLifecycle(emptyList())
    val chats by remember(uid) { AppGraph.chats.chats(uid) }.collectAsStateWithLifecycle(emptyList())
    return Badges(
        requests = incoming.count { it.status == RequestStatus.PENDING },
        invites = invites.size,
        chats = chats.count { it.isUnread(uid) },
    )
}
