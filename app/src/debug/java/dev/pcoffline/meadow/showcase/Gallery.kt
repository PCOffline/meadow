package dev.pcoffline.meadow.showcase

import androidx.compose.foundation.layout.fillMaxWidth
import dev.pcoffline.meadow.ui.components.MeadowCard
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
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.LimitRequest
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
import dev.pcoffline.meadow.ui.limits.AsNowNote
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.DurationKeypadDialog
import dev.pcoffline.meadow.ui.limits.FineTune
import dev.pcoffline.meadow.ui.limits.FineTuneSheet
import dev.pcoffline.meadow.ui.limits.KeypadChoice
import dev.pcoffline.meadow.ui.limits.Picked
import dev.pcoffline.meadow.ui.limits.Stretch
import dev.pcoffline.meadow.ui.limits.WeekCard
import dev.pcoffline.meadow.ui.limits.WeekGuide
import dev.pcoffline.meadow.ui.limits.WeekLimit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TimerOff
import java.time.DayOfWeek
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.components.flora.namedPlace
import dev.pcoffline.meadow.data.PLANTS_PER_THEME
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
import androidx.compose.runtime.CompositionLocalProvider
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.ui.components.LocalSkyWeather

/**
 * Debug builds only: every screen in each of its states, drawn from [DemoGarden], with the theme,
 * day or night, the weather in the sky and being offline switched from the list. Opened from its own launcher icon,
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
    // Null follows the phone's own weather setting.
    var weather by rememberSaveable { mutableStateOf<Weather?>(null) }
    // Offline here is pretend: the real state comes back on leaving the gallery.
    val wasOnline = remember { Connectivity.online.value }
    DisposableEffect(offline) {
        Connectivity.set(!offline)
        onDispose { Connectivity.set(wasOnline) }
    }
    MeadowTheme(theme, mode) {
        CompositionLocalProvider(LocalSkyWeather provides weather) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val index = open
                if (index == null) {
                    ScenarioList(
                        scenarios, theme, mode, offline, weather,
                        onTheme = { theme = it }, onMode = { mode = it }, onOffline = { offline = it }, onWeather = { weather = it }, onOpen = { open = it },
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
}

@Composable
private fun ScenarioList(
    scenarios: List<Scenario>,
    theme: GardenTheme,
    mode: DarkMode,
    offline: Boolean,
    weather: Weather?,
    onTheme: (GardenTheme) -> Unit,
    onMode: (DarkMode) -> Unit,
    onOffline: (Boolean) -> Unit,
    onWeather: (Weather?) -> Unit,
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
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(null) + Weather.entries).forEach { w ->
                    val label = w?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Phone's weather"
                    FilterChip(selected = w == weather, onClick = { onWeather(w) }, label = { Text(label) })
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

    fun editor(bumblebees: Int, existing: AppLimit? = youtube, pending: Boolean = false, waiting: LimitRequest? = null) = LimitEditorState(
        existing = existing, appName = "YouTube", budget = youtubeBudget, bumblebeeCount = bumblebees,
        pending = waiting ?: DemoGarden.waitingAsk.copy(packageName = "demo.youtube", appName = "YouTube").takeIf { pending }, loaded = true,
    )

    /**
     * A full week: weekdays 10:00–22:00, Friday also 22:00 to 02:00 on Saturday, weekends
     * 06:00–22:00 and Sunday resting. Caps at weekends, and one past midnight on Friday. Saturday
     * has an hour of its own, and Sunday no limit.
     */
    val week = WeekLimit(
        schedule = Schedule(
            (1..4).associateWith { listOf(TimeWindow(600, 1320)) } +
                mapOf(5 to listOf(TimeWindow(600, 1080), TimeWindow(1320, 1440)), 6 to listOf(TimeWindow(0, 120), TimeWindow(360, 1320)), 7 to emptyList()),
        ),
        caps = listOf(TimeCap(listOf(6), 840, 1320, 30), TimeCap(listOf(5), 1320, 1560, 20)),
        daily = 30,
        weekly = 180,
        dayMinutes = mapOf(6 to 60, 7 to 0),
    )

    /** Ana's Instagram limit as it is, and her question to change it. */
    private val anaLimit = AppLimit(
        "demo.instagram", "Instagram", 30, 180,
        schedule = Schedule((1..7).associateWith { if (it == 7) emptyList() else listOf(TimeWindow(10 * 60, 16 * 60)) }),
        caps = listOf(TimeCap(listOf(7), 8 * 60, 12 * 60, 60)),
    )
    val changeFromAna = DemoGarden.askFromAna.copy(
        id = "r4", type = RequestType.CHANGE_LIMIT, minutes = 0, dailyMinutes = 45, weeklyMinutes = 180,
        schedule = Schedule((1..7).associateWith { if (it == 6) listOf(TimeWindow(10 * 60, 20 * 60)) else if (it == 7) listOf(TimeWindow.ALL_DAY) else listOf(TimeWindow(10 * 60, 16 * 60)) }),
        caps = listOf(TimeCap(listOf(1, 2, 3, 4, 5), 18 * 60, 22 * 60, 30)),
        message = "Weekends are for long calls with family.", usedMinutes = 25, allowedMinutes = 30, previous = anaLimit,
    )

    /** The same question, as one asked before Meadow kept the limit as it was. */
    val olderChangeFromAna = changeFromAna.copy(previous = null)

    /** Robin's own question to change YouTube's limit, still waiting. */
    val waitingChange = changeFromAna.copy(
        id = "r5", requesterId = DemoGarden.robin.uid, requesterName = DemoGarden.robin.displayName,
        packageName = "demo.youtube", appName = "YouTube", message = "", previous = youtube.copy(schedule = anaLimit.schedule, caps = anaLimit.caps),
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
    ) + Weather.entries.filter { it != Weather.CLEAR }.map { weather ->
        // The sky in each weather, over the lived-in garden; the clear sky is the state above.
        Scenario("Garden", "Weather: ${weather.name.lowercase().replace('_', ' ')}") {
            CompositionLocalProvider(LocalSkyWeather provides weather) { WithTabs(Tab.GARDEN) { GardenContent(d.robin, d.garden) } }
        }
    } + listOf(

        // The theme's five plants, as the time of their apps goes.
        Scenario("Plants", "All the time left") { Page { PlantSheet(1f) } },
        Scenario("Plants", "A third of the time left") { Page { PlantSheet(0.33f) } },
        Scenario("Plants", "A tenth of the time left") { Page { PlantSheet(0.1f) } },
        Scenario("Plants", "Time used up") { Page { PlantSheet(0f) } },
        Scenario("Plants", "Asleep") { Page { PlantSheet(1f, asleep = true) } },

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
        Scenario("People", "Bumblebees, no gardens yet") {
            WithTabs(Tab.PEOPLE) { PeopleContent(d.robin.copy(approveeIds = emptyList()), People(bumblebees = d.people.bumblebees, chats = d.people.chats)) }
        },
        Scenario("People", "Nobody wrote a bio") {
            WithTabs(Tab.PEOPLE) {
                PeopleContent(d.robin, People(bumblebees = d.people.bumblebees.map { it.copy(bio = "") }, lookingAfter = d.people.lookingAfter.map { it.copy(bio = "") }))
            }
        },
        Scenario("People", "Your answers as a Bumblebee") { Page { BeeStatsContent(d.beeStats) } },
        Scenario("People", "Your answers, none yet") { Page { BeeStatsContent(Demo.noAnswers) } },
        Scenario("People", "A friend's garden, all shared") { FriendDemo(d.anaPage) },
        Scenario("People", "A friend's garden, nothing shared") { FriendDemo(SharedPage(d.ana, null, null, null, null)) },
        Scenario("People", "A friend's garden, loading") { FriendDemo(d.anaPage, waiting = true) },

        Scenario("Chats", "A conversation") { Page { ChatDemo() } },
        Scenario("Chats", "A message that didn't send") { Page { ChatDemo(error = "Something went wrong. Try again.") } },
        Scenario("Chats", "A change to a limit, with what changes") { Page { ChatDemo(request = Demo.changeFromAna, from = d.ana) } },
        Scenario("People", "A change to a limit, with what changes") { ChangeDemo(Demo.changeFromAna) },
        Scenario("People", "A change asked before Meadow kept the old limit") { ChangeDemo(Demo.olderChangeFromAna) },
        Scenario("Chats", "A closed chat") { Page { ChatContent(d.robin, null, emptyList()) } },
        Scenario("Chats", "Loading") { Page { ChatContent(d.robin, null, emptyList(), loading = true) } },

        Scenario("Limit", "Planting a new one") { EditorDemo(Demo.editor(bumblebees = 2, existing = null)) },
        Scenario("Limit", "Tending one, no Bumblebees") { EditorDemo(Demo.editor(bumblebees = 0)) },
        Scenario("Limit", "Tending one, with its plant chosen") {
            EditorDemo(Demo.editor(bumblebees = 0, existing = Demo.youtube.copy(plant = (namedPlace(Demo.youtube.packageName) + 2) % PLANTS_PER_THEME)))
        },
        Scenario("Limit", "Tending one, with Bumblebees (loosening asks)") { EditorDemo(Demo.editor(bumblebees = 2)) },
        Scenario("Limit", "Your week, with hours, caps and days' own limits") { WeekDemo(Demo.week) },
        Scenario("Limit", "Your week, a stretch picked") { WeekDemo(Demo.week, Picked.Hours(Stretch(5, 1320, 1560))) },
        Scenario("Limit", "Your week, a cap past midnight picked") { WeekDemo(Demo.week, Picked.Cap(1)) },
        Scenario("Limit", "Your week, a day picked") { WeekDemo(Demo.week, Picked.Day(6)) },
        Scenario("Limit", "Your week, nothing drawn yet") { WeekDemo(WeekLimit(null, emptyList(), 30, 0, emptyMap())) },
        Scenario("Limit", "Your week, zoomed in for 5-minute steps") { WeekDemo(Demo.week, step = 5) },
        Scenario("Limit", "Your week, waiting for an answer") { WeekDemo(Demo.week, waiting = true) },
        Scenario("Limit", "The keypad for an amount") {
            Page {
                DurationKeypadDialog(
                    "Daily limit", Icons.Outlined.Today, 90, 24 * 60, "Up to 24h a day.", listOf(15, 30, 45, 60, 90, 120), onDismiss = {},
                    choices = listOf(KeypadChoice(Icons.Outlined.TimerOff, "No limit") {}),
                ) {}
            }
        },
        Scenario("Limit", "Fine-tune hours") { Page { FineTuneSheet(FineTune(listOf(5), 1320, 1560), DayOfWeek.MONDAY, onDismiss = {}) {} } },
        Scenario("Limit", "Fine-tune a cap") { Page { FineTuneSheet(FineTune(listOf(6, 7), 840, 1320, 30), DayOfWeek.MONDAY, onDismiss = {}) {} } },
        Scenario("Limit", "The guide to the week") { Page { WeekGuide("YouTube", DayOfWeek.MONDAY) {} } },
        Scenario("Limit", "Waiting on a question") { EditorDemo(Demo.editor(bumblebees = 2, pending = true)) },
        Scenario("Limit", "Waiting on a change, with what changes") {
            EditorDemo(Demo.editor(bumblebees = 2, existing = Demo.waitingChange.previous, waiting = Demo.waitingChange))
        },

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
        Scenario("Protections", "Strict mode needs two settings") { Page { StrictGateContent(Demo.protection(essentials = true, rest = false)) } },

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
private fun ChatDemo(error: String? = null, request: LimitRequest = DemoGarden.request, from: UserProfile = DemoGarden.robin) {
    ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, error = error, requestCard = { _, _ ->
        RequestCard(request.copy(requesterId = from.uid, requesterName = from.displayName), DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp), compact = true)
    })
}

/** People, with one question waiting for Robin: [request]. */
@Composable
private fun ChangeDemo(request: LimitRequest) {
    WithTabs(Tab.PEOPLE) {
        PeopleContent(DemoGarden.robin, DemoGarden.people.copy(incomingRequests = listOf(request), incomingInvites = emptyList(), outgoingRequests = emptyList()))
    }
}

/** The week card alone, on a page, as the editor shows it. */
@Composable
private fun WeekDemo(start: WeekLimit, picked: Picked? = null, step: Int = 15, waiting: Boolean = false) {
    var limit by remember { mutableStateOf(start) }
    Page {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            MeadowCard(Modifier.fillMaxWidth()) {
                WeekCard(limit, DayOfWeek.MONDAY, step, enabled = !waiting, onChange = { limit = it }, onStep = {}, onHelp = {}, waiting = waiting, startPicked = picked)
                if (waiting) AsNowNote()
            }
        }
    }
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
