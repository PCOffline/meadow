package dev.pcoffline.meadow.showcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.BeeStats
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.SharedPage
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.PhoneMaker
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.enforcement.RestContent
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.auth.AuthScreen
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.components.OfflineBar
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.TabTitle
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import dev.pcoffline.meadow.ui.requests.BeeStatsContent
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.settings.PrivacyPreviewContent
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.setup.SetupScreen
import dev.pcoffline.meadow.ui.setup.StrictGateContent
import dev.pcoffline.meadow.ui.setup.TroubleshootContent
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.PeriodStats
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.stats.StatsUiState
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import java.time.LocalDate

/**
 * Debug builds only: every screen in each of its states, drawn from [DemoGarden], with the theme,
 * day or night and being offline switched from the list. Opened from its own launcher icon,
 * "Meadow screens". The data is made up; what a screen does when tapped (saving a limit, say)
 * still acts on whoever is signed in to this build.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Gallery() }
    }
}

/** One state of one screen. */
internal class Scenario(val screen: String, val name: String, val content: @Composable () -> Unit)

@Composable
internal fun Gallery(scenarios: List<Scenario> = Scenarios.all) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var theme by rememberSaveable { mutableStateOf(GardenTheme.MEADOW) }
    var mode by rememberSaveable { mutableStateOf(DarkMode.LIGHT) }
    var offline by rememberSaveable { mutableStateOf(false) }
    // Offline here is pretend: the real state comes back on leaving the gallery.
    val wasOnline = remember { Connectivity.online.value }
    DisposableEffect(offline) {
        Connectivity.set(!offline)
        onDispose { Connectivity.set(wasOnline) }
    }
    MeadowTheme(theme, mode) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val index = open
            if (index == null) {
                ScenarioList(
                    scenarios, theme, mode, offline,
                    onTheme = { theme = it }, onMode = { mode = it }, onOffline = { offline = it }, onOpen = { open = it },
                )
            } else {
                BackHandler { open = null }
                // As in the app: below the status bar, with the offline strip on top.
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    OfflineBar()
                    Box(Modifier.weight(1f)) { SnackbarScope { scenarios[index].content() } }
                }
            }
        }
    }
}

@Composable
private fun ScenarioList(
    scenarios: List<Scenario>,
    theme: GardenTheme,
    mode: DarkMode,
    offline: Boolean,
    onTheme: (GardenTheme) -> Unit,
    onMode: (DarkMode) -> Unit,
    onOffline: (Boolean) -> Unit,
    onOpen: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        item { TabTitle(stringResource(R.string.gallery_label)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GardenTheme.entries.forEach { t ->
                    FilterChip(selected = t == theme, onClick = { onTheme(t) }, label = { Text("${t.emoji} ${stringResource(t.label)}") })
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMode.entries.forEach { m ->
                    FilterChip(selected = m == mode, onClick = { onMode(m) }, label = { Text(stringResource(m.label)) })
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Offline") },
                trailingContent = { Switch(checked = offline, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.toggleable(value = offline, role = Role.Switch, onValueChange = onOffline),
            )
        }
        scenarios.withIndex().groupBy { it.value.screen }.forEach { (screen, states) ->
            item(key = screen) { SectionTitle(screen, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
            items(states, key = { "${it.value.screen}/${it.value.name}" }) { (i, scenario) ->
                ListItem(
                    headlineContent = { Text(scenario.name) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { onOpen(i) },
                )
            }
        }
    }
}

@Composable
private fun WithTabs(tab: Tab, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MeadowTabBar(tab, Badges(requests = 1, invites = 1, chats = 1)) {} },
    ) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() } }
}

/** A screen of its own, below the status bar and above the navigation bar. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
}

/** Growth, with its periods and days switched as in the app. */
@Composable
private fun Growth(start: StatsUiState) {
    var state by remember { mutableStateOf(start) }
    WithTabs(Tab.STATS) {
        StatsContent(
            state,
            onSelect = { state = if (it == Period.MONTH) DemoGarden.monthStats else if (it == Period.WEEK) DemoGarden.stats else Demo.day(Demo.yesterday) },
            onOpenDay = { state = Demo.day(it) },
        )
    }
}

/** Made-up states the screens need beyond [DemoGarden]'s. */
private object Demo {
    private const val MIN = 60_000L
    private val now get() = System.currentTimeMillis()
    val yesterday: LocalDate get() = LocalDate.now().minusDays(1)

    fun day(day: LocalDate): StatsUiState {
        val selection = Selection.ofDay(day, LocalDate.now())
        val apps = DemoGarden.stats.stats!!.apps.map { it.copy(ms = it.ms / 7) }
        return StatsUiState(selection, PeriodStats(selection, day, day, listOf(day to apps.sumOf { it.ms }), apps, apps.sumOf { it.ms }, 150 * MIN))
    }

    val missingDays: StatsUiState = DemoGarden.stats.let { s ->
        val stats = s.stats!!
        s.copy(stats = stats.copy(days = stats.days.mapIndexed { i, (d, ms) -> d to ms.takeIf { i > 1 } }, previousTotalMs = null))
    }

    val emergencyGarden: GardenState
        get() = DemoGarden.garden.copy(budgets = DemoGarden.garden.budgets.map { it.copy(emergencyUntilMillis = now + 30 * MIN) })

    val youtube = AppLimit("demo.youtube", "YouTube", 60, 0)
    private val youtubeBudget get() = AppBudget(youtube, 52 * MIN, 240 * MIN, 0, 0)

    fun editor(bumblebees: Int, existing: AppLimit? = youtube, pending: Boolean = false) = LimitEditorState(
        existing = existing, appName = "YouTube", budget = youtubeBudget, bumblebeeCount = bumblebees,
        pending = DemoGarden.waitingAsk.copy(packageName = "demo.youtube", appName = "YouTube").takeIf { pending }, loaded = true,
    )

    private val rest = DemoGarden.rest
    val restWeek get() = rest.copy(budget = DemoGarden.budget("demo.tiktok", "TikTok", daily = 0, usedToday = 40, weekly = 300, usedWeek = 300))
    val restHours
        get() = rest.copy(
            budget = AppBudget(
                AppLimit("demo.tiktok", "TikTok", 0, 0, Schedule.everyDay(TimeWindow(17 * 60, 20 * 60))),
                usedTodayMs = 0, usedWeekMs = 0, bonusTodayMinutes = 0, bonusWeekMinutes = 0,
                outsideHours = true, untilScheduleChangeMs = 3 * 60 * MIN,
            ),
        )
    val restAsked get() = rest.copy(pendingRequest = DemoGarden.waitingAsk)
    val restYes get() = rest.copy(budget = DemoGarden.tiktok.copy(bonusTodayMinutes = 15))
    val restEmergency get() = rest.copy(budget = DemoGarden.tiktok.copy(emergencyUntilMillis = now + 30 * MIN))

    fun protection(essentials: Boolean, rest: Boolean) = ProtectionState(
        usageAccess = essentials, accessibility = essentials, notifications = rest, overlay = rest,
        deviceAdmin = rest, batteryUnrestricted = rest, notificationListener = essentials,
    )

    val noAnswers = BeeStats(0, 0, 0, 0, 0, 0, emptyList(), leniency = null)
}

internal object Scenarios {
    private val d = DemoGarden
    private val nobody = d.robin.copy(approverIds = emptyList(), approveeIds = emptyList())

    val all: List<Scenario> = listOf(
        Scenario("Welcome", "Intro, name and sign-in") { AuthScreen() },

        Scenario("Garden", "Lived in") { WithTabs(Tab.GARDEN) { GardenContent(d.robin, d.garden) } },
        Scenario("Garden", "Nothing planted yet") { WithTabs(Tab.GARDEN) { GardenContent(nobody, GardenState(loaded = true)) } },
        Scenario("Garden", "Setup unfinished") { WithTabs(Tab.GARDEN) { GardenContent(d.robin, d.garden.copy(essentialsReady = false)) } },
        Scenario("Garden", "Battery saver may stop Meadow") { WithTabs(Tab.GARDEN) { GardenContent(d.robin, d.garden.copy(batteryRestricted = true)) } },
        Scenario("Garden", "Emergency unlock on") { WithTabs(Tab.GARDEN) { GardenContent(d.robin, Demo.emergencyGarden) } },
        Scenario("Garden", "Loading") { WithTabs(Tab.GARDEN) { GardenContent(d.robin, GardenState()) } },

        Scenario("Growth", "Week (tap Day, Week, Month, or a day of the month)") { Growth(d.stats) },
        Scenario("Growth", "Month") { Growth(d.monthStats) },
        Scenario("Growth", "A day") { Growth(Demo.day(Demo.yesterday)) },
        Scenario("Growth", "Days with no record") { Growth(Demo.missingDays) },
        Scenario("Growth", "No usage access") { WithTabs(Tab.STATS) { StatsContent(StatsUiState(hasPermission = false)) } },
        Scenario("Growth", "Loading") { WithTabs(Tab.STATS) { StatsContent(StatsUiState()) } },

        Scenario("People", "Everyone, with what's waiting") { WithTabs(Tab.PEOPLE) { PeopleContent(d.robin, d.people) } },
        Scenario("People", "Invites, to you and from you") {
            WithTabs(Tab.PEOPLE) {
                PeopleContent(d.robin, People(listOf(d.maya), listOf(d.ana), listOf(d.inviteToRobin), listOf(d.inviteFromRobin)))
            }
        },
        Scenario("People", "Nobody yet") { WithTabs(Tab.PEOPLE) { PeopleContent(nobody, People()) } },
        Scenario("People", "Your answers as a Bumblebee") { Page { BeeStatsContent(d.beeStats) } },
        Scenario("People", "Your answers, none yet") { Page { BeeStatsContent(Demo.noAnswers) } },
        Scenario("People", "A friend's garden, all shared") { FriendDemo(d.anaPage) },
        Scenario("People", "A friend's garden, nothing shared") { FriendDemo(SharedPage(d.ana, null, null, null, null)) },
        Scenario("People", "A friend's garden, loading") { FriendDemo(d.anaPage, waiting = true) },

        Scenario("Chats", "A conversation") { Page { ChatDemo() } },
        Scenario("Chats", "A message that didn't send") { Page { ChatDemo(error = "Something went wrong. Please try again.") } },
        Scenario("Chats", "A closed chat") { Page { ChatContent(d.robin, null, emptyList()) } },
        Scenario("Chats", "Loading") { Page { ChatContent(d.robin, null, emptyList(), loading = true) } },

        Scenario("Limit", "Planting a new one") { EditorDemo(Demo.editor(bumblebees = 2, existing = null)) },
        Scenario("Limit", "Tending one, no Bumblebees") { EditorDemo(Demo.editor(bumblebees = 0)) },
        Scenario("Limit", "Tending one, with Bumblebees (loosening asks)") { EditorDemo(Demo.editor(bumblebees = 2)) },
        Scenario("Limit", "Waiting on a question") { EditorDemo(Demo.editor(bumblebees = 2, pending = true)) },

        Scenario("Resting app", "Time's up today") { RestContent("demo.tiktok", d.rest) },
        Scenario("Resting app", "This week's time is used") { RestContent("demo.tiktok", Demo.restWeek) },
        Scenario("Resting app", "Outside its hours") { RestContent("demo.tiktok", Demo.restHours) },
        Scenario("Resting app", "Asked, waiting") { RestContent("demo.tiktok", Demo.restAsked) },
        Scenario("Resting app", "No Bumblebees") { RestContent("demo.tiktok", d.rest.copy(hasBumblebees = false)) },
        Scenario("Resting app", "Signed out") { RestContent("demo.tiktok", d.rest.copy(signedIn = false)) },
        Scenario("Resting app", "A Bumblebee approved") { RestContent("demo.tiktok", Demo.restYes) },
        Scenario("Resting app", "Emergency unlock") { RestContent("demo.tiktok", Demo.restEmergency) },

        Scenario("Protections", "Nothing on yet") { Page { SetupScreen(onDone = {}, read = { Demo.protection(essentials = false, rest = false) }) } },
        Scenario("Protections", "Essentials on") { Page { SetupScreen(onDone = {}, read = { Demo.protection(essentials = true, rest = false) }) } },
        Scenario("Protections", "Everything on") { Page { SetupScreen(onDone = {}, read = { Demo.protection(essentials = true, rest = true) }) } },
        Scenario("Protections", "Troubleshoot, essentials off") { TroubleshootDemo(Demo.protection(essentials = false, rest = false)) },
        Scenario("Protections", "Troubleshoot") { TroubleshootDemo(Demo.protection(essentials = true, rest = false)) },
        Scenario("Protections", "Strict mode needs a hand") { Page { StrictGateContent(Demo.protection(essentials = true, rest = false)) } },

        Scenario("Settings", "Settings") { Page { SettingsScreen(d.robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) } },
        Scenario("Settings", "What your Bumblebees see") { Page { PrivacyPreviewContent(d.robin, d.today, d.progress, d.myLimits, save = {}) } },
        Scenario("Settings", "Profile") { Page { ProfileScreen(d.robin, onBack = {}) } },
    )
}

@Composable
private fun FriendDemo(page: SharedPage, waiting: Boolean = false) {
    Page {
        Column(Modifier.fillMaxSize()) {
            BackTopBar(page.profile.displayName, onBack = {})
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) { FriendPage(page, waiting) }
        }
    }
}

@Composable
private fun ChatDemo(error: String? = null) {
    ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, error = error, requestCard = { _, _ ->
        RequestCard(DemoGarden.request, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp), compact = true)
    })
}

@Composable
private fun EditorDemo(state: LimitEditorState) {
    Page {
        Column(Modifier.fillMaxSize()) {
            BackTopBar(state.appName, onBack = {})
            LimitEditor(Demo.youtube.packageName, state, onDone = {})
        }
    }
}

@Composable
private fun TroubleshootDemo(state: ProtectionState) {
    val context = LocalContext.current
    val steps = remember { PhoneMaker.steps(context) }
    Page { TroubleshootContent(state, steps) }
}
