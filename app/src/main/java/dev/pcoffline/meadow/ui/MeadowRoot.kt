package dev.pcoffline.meadow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.auth.EmailLinkDialog
import dev.pcoffline.meadow.ui.auth.ProfileSetupScreen
import dev.pcoffline.meadow.ui.chat.ChatScreen
import dev.pcoffline.meadow.ui.circle.AcceptInviteDialog
import dev.pcoffline.meadow.ui.circle.PeopleScreen
import dev.pcoffline.meadow.ui.circle.FriendScreen
import dev.pcoffline.meadow.ui.components.AccountEntry
import dev.pcoffline.meadow.ui.components.LocalAccount
import dev.pcoffline.meadow.ui.components.OfflineBar
import dev.pcoffline.meadow.ui.components.PetalBurst
import dev.pcoffline.meadow.ui.home.GardenScreen
import dev.pcoffline.meadow.ui.legal.LegalDoc
import dev.pcoffline.meadow.ui.legal.LegalScreen
import dev.pcoffline.meadow.ui.legal.TermsUpdateDialog
import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.limits.LimitEditorScreen
import dev.pcoffline.meadow.ui.navigation.ChatRoute
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import dev.pcoffline.meadow.ui.navigation.FriendRoute
import dev.pcoffline.meadow.ui.navigation.GardenRoute
import dev.pcoffline.meadow.ui.navigation.LegalRoute
import dev.pcoffline.meadow.ui.navigation.LimitRoute
import dev.pcoffline.meadow.ui.navigation.ProfileRoute
import dev.pcoffline.meadow.ui.navigation.PickAppRoute
import dev.pcoffline.meadow.ui.navigation.PrivacyPreviewRoute
import dev.pcoffline.meadow.ui.navigation.PeopleRoute
import dev.pcoffline.meadow.ui.navigation.SettingsRoute
import dev.pcoffline.meadow.ui.navigation.SetupRoute
import dev.pcoffline.meadow.ui.navigation.StatsRoute
import dev.pcoffline.meadow.ui.navigation.TroubleshootRoute
import dev.pcoffline.meadow.ui.navigation.BeeStatsRoute
import dev.pcoffline.meadow.ui.settings.PrivacyPreviewScreen
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.setup.BackendMissingScreen
import dev.pcoffline.meadow.ui.setup.SetupScreen
import dev.pcoffline.meadow.ui.setup.StrictGate
import dev.pcoffline.meadow.ui.setup.TroubleshootScreen
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.requests.BeeStatsScreen
import dev.pcoffline.meadow.ui.stats.StatsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun MeadowRoot(pendingRoute: MutableStateFlow<String?>) {
    if (!AppGraph.backendReady) {
        BackendMissingScreen()
        return
    }
    // Onboarding and the sign-in link dialog show their snackbars here; the tabs have their own.
    SnackbarScope { MeadowScreens(pendingRoute) }
}

@Composable
private fun MeadowScreens(pendingRoute: MutableStateFlow<String?>) {
    val state by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val creating by AppGraph.auth.creatingProfile.collectAsStateWithLifecycle()
    val anonymous by AppGraph.auth.anonymous.collectAsStateWithLifecycle()
    val s = state
    val signInLink by AppGraph.auth.incomingLink.collectAsStateWithLifecycle()
    signInLink?.let { EmailLinkDialog(it) }
    // Onboarding stays put while a new garden is planted, and after a failed try (so its error
    // shows), instead of flashing the "Almost there" screen meant for accounts with an email.
    val onboarding = s == ProfileState.SignedOut || creating || (s == ProfileState.Missing && anonymous)
    if (onboarding) {
        AuthScreen()
    } else {
        when (s) {
            ProfileState.Loading -> StartupLoading()
            is ProfileState.Unavailable -> GardenUnavailable(s.cause)
            ProfileState.Missing -> ProfileSetupScreen()
            is ProfileState.Ready -> MainScaffold(s.profile, pendingRoute)
            ProfileState.SignedOut -> Unit
        }
    }
}

internal enum class Tab(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    GARDEN(GardenRoute, { it.hasRoute<GardenRoute>() }, R.string.tab_garden, Icons.Outlined.LocalFlorist),
    PEOPLE(PeopleRoute, { it.hasRoute<PeopleRoute>() }, R.string.tab_people, Icons.Outlined.People),
    STATS(StatsRoute, { it.hasRoute<StatsRoute>() }, R.string.tab_growth, Icons.Outlined.BarChart),
}

/** What's waiting on the People tab: requests to answer, invites, and unread chats. */
data class Badges(val requests: Int = 0, val invites: Int = 0, val chats: Int = 0) {
    val people: Int get() = requests + invites + chats
}

class MainViewModel(uid: String) : ViewModel() {
    val badges: StateFlow<Badges> = combine(
        AppGraph.requests.incoming(uid),
        AppGraph.social.incomingInvites(uid),
        AppGraph.chats.chats(uid),
    ) { incoming, invites, chats ->
        Badges(
            requests = incoming.count { it.status == RequestStatus.PENDING },
            invites = invites.size,
            chats = chats.count { it.isUnread(uid) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Badges())
}

@Composable
private fun MainScaffold(profile: UserProfile, pendingRoute: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val main = viewModel(key = profile.uid) { MainViewModel(profile.uid) }
    val badges by main.badges.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = destination?.let { d -> Tab.entries.firstOrNull { it.matches(d) } }

    var celebration by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val celebrate: () -> Unit = remember {
        {
            celebration++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Offered once per visit: after a rotation the restored back stack already has it.
    var setupOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (setupOffered) return@LaunchedEffect
        setupOffered = true
        if (!AppGraph.protection.current().essentialsReady) nav.navigate(SetupRoute)
    }
    var askFor by rememberSaveable { mutableStateOf<String?>(null) }
    val link by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        DeepLinks.askFor(l)?.let {
            pendingRoute.value = null
            askFor = it
            return@LaunchedEffect
        }
        val target = DeepLinks.toRoute(l) ?: return@LaunchedEffect
        pendingRoute.value = null
        nav.navigate(target) { launchSingleTop = true }
    }
    val pendingInvite by AppGraph.store.pendingInvite.collectAsStateWithLifecycle()
    val acceptedTerms by AppGraph.store.acceptedTerms.collectAsStateWithLifecycle()
    val accountConfirmed by AppGraph.sync.accountConfirmed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clearance = remember { SnackbarClearance() }
    val account = remember(profile.avatar) { AccountEntry(profile.avatar) { nav.navigate(ProfileRoute) { launchSingleTop = true } } }

    CompositionLocalProvider(
        LocalSnackbar provides snackbar,
        LocalCelebrate provides celebrate,
        LocalSnackbarClearance provides clearance,
        LocalAccount provides account,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = clearance.height)) },
            bottomBar = {
                if (currentTab != null) MeadowTabBar(currentTab, badges) { nav.switchTab(it.route) }
            },
        ) { padding ->
            // Consumed, so screens' own status bar and keyboard padding doesn't add the bars twice.
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                OfflineBar()
                Box(Modifier.weight(1f)) {
                    NavHost(
                        nav,
                        startDestination = GardenRoute,
                        enterTransition = { fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 28 } },
                        exitTransition = { fadeOut(tween(180)) },
                        popEnterTransition = { fadeIn(tween(260)) },
                        popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 28 } },
                    ) {
                        composable<SetupRoute> {
                            SetupScreen(onDone = { nav.popBackStack() }, onTroubleshoot = { nav.navigate(TroubleshootRoute) }, profile = profile)
                        }
                        composable<TroubleshootRoute> {
                            TroubleshootScreen(onBack = { nav.popBackStack() }, onOpenSetup = { nav.navigate(SetupRoute) })
                        }
                        composable<BeeStatsRoute> { BeeStatsScreen(profile, onBack = { nav.popBackStack() }) }
                        composable<GardenRoute> {
                            GardenScreen(
                                profile = profile,
                                onOpenSettings = { nav.navigate(SettingsRoute) },
                                onOpenProfile = { nav.navigate(ProfileRoute) },
                                onOpenSetup = { nav.navigate(SetupRoute) },
                                onPlantLimit = { nav.navigate(PickAppRoute) },
                                onOpenLimit = { nav.navigate(LimitRoute(it)) },
                                onOpenTroubleshoot = { nav.navigate(TroubleshootRoute) },
                            )
                        }
                        composable<StatsRoute> { StatsScreen(onOpenSetup = { nav.navigate(SetupRoute) }, onOpenApp = { nav.navigate(LimitRoute(it)) }) }
                        composable<PeopleRoute> {
                            PeopleScreen(profile, onOpenChat = { nav.navigate(ChatRoute(it)) }, onOpenBeeStats = { nav.navigate(BeeStatsRoute) })
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                profile = profile,
                                onBack = { nav.popBackStack() },
                                onOpenSetup = { nav.navigate(SetupRoute) },
                                onOpenProfile = { nav.navigate(ProfileRoute) },
                                onOpenLegal = { nav.navigate(LegalRoute(it.name)) },
                                onOpenTroubleshoot = { nav.navigate(TroubleshootRoute) },
                                onOpenPrivacyPreview = { nav.navigate(PrivacyPreviewRoute) },
                            )
                        }
                        composable<PrivacyPreviewRoute> { PrivacyPreviewScreen(profile, onBack = { nav.popBackStack() }) }
                        composable<ProfileRoute> { ProfileScreen(profile, onBack = { nav.popBackStack() }) }
                        composable<LegalRoute> { entry ->
                            LegalScreen(LegalDoc.fromName(entry.toRoute<LegalRoute>().doc), onBack = { nav.popBackStack() })
                        }
                        composable<PickAppRoute> {
                            AppPickerScreen(
                                onBack = { nav.popBackStack() },
                                onPick = { nav.navigate(LimitRoute(it)) { popUpTo<PickAppRoute> { inclusive = true } } },
                            )
                        }
                        composable<LimitRoute> { entry ->
                            LimitEditorScreen(entry.toRoute<LimitRoute>().packageName, onDone = { nav.popBackStack() })
                        }
                        composable<ChatRoute> { entry ->
                            ChatScreen(profile, entry.toRoute<ChatRoute>().chatId, onBack = { nav.popBackStack() }, onOpenGarden = { nav.navigate(FriendRoute(it)) })
                        }
                        composable<FriendRoute> { entry ->
                            FriendScreen(
                                me = profile,
                                uid = entry.toRoute<FriendRoute>().uid,
                                onBack = { nav.popBackStack() },
                                onOpenChat = { nav.navigate(ChatRoute(it)) },
                            )
                        }
                    }
                    PetalBurst(celebration, Modifier.fillMaxSize())
                }
            }
        }
        askFor?.let { AskForTimeDialog(packageName = it, onDismiss = { askFor = null }) }
        if (strictGateCovers(destination)) StrictGate(profile.strictMode, onTroubleshoot = { nav.navigate(TroubleshootRoute) })
        if (accountConfirmed && acceptedTerms < LegalDoc.CURRENT_VERSION) {
            TermsUpdateDialog(onAccept = { scope.launch { AppGraph.profiles.acceptTerms(LegalDoc.CURRENT_VERSION) } })
        }
        pendingInvite?.let { token ->
            AcceptInviteDialog(token = token, onClose = { AppGraph.store.setPendingInvite(null) })
        }
    }
}

/**
 * Whether the strict gate covers [destination]: every screen but Troubleshoot, which the gate
 * opens to help fix what's missing and so mustn't hide.
 */
internal fun strictGateCovers(destination: NavDestination?): Boolean = destination?.hasRoute<TroubleshootRoute>() != true

/** The bottom tab bar, with counts for new requests, invites and messages. */
@Composable
internal fun MeadowTabBar(current: Tab, badges: Badges, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Tab.entries.forEach { tab ->
            val count = if (tab == Tab.PEOPLE) badges.people else 0
            TabItem(tab, selected = tab == current, count = count) { onSelect(tab) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, selected: Boolean, count: Int, onClick: () -> Unit) {
    val iconScale by animateFloatAsState(
        if (selected) 1.18f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tabIcon",
    )
    val label = stringResource(tab.label)
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                // The label below already names the tab; naming the icon too made TalkBack say it twice.
                Icon(tab.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
            }
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

fun NavController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
