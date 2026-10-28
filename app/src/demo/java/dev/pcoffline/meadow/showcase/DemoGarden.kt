package dev.pcoffline.meadow.showcase

import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.BeeStats
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Invite
import dev.pcoffline.meadow.data.InviteStatus
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.SharedAppUsage
import dev.pcoffline.meadow.data.SharedPage
import dev.pcoffline.meadow.data.SharedUsage
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.RestState
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.stats.AppTime
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.PeriodStats
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsUiState

/**
 * A made-up but lived-in garden: Robin, two Bumblebees and a friend. Drawn by the screen gallery
 * in debug builds, the tests and the README's screenshots; never part of a release.
 */
object DemoGarden {
    private const val MIN = 60_000L
    private val now = System.currentTimeMillis()

    /** Every part shared, so the showcase has a full page to show. */
    val sharesEverything = Privacy(shareLimits = true, shareTime = true, shareApps = true, shareProgress = true)

    private fun person(uid: String, name: String, emoji: String, color: String, bio: String = "") = UserProfile(
        uid = uid, displayName = name, avatar = Avatar(emoji, color, null), bio = bio, friendCode = "PETAL7",
        approverIds = emptyList(), approveeIds = emptyList(), privacy = sharesEverything, strictMode = true,
        emergencyUntilMillis = 0L, emergencyPackage = null, emergencyUsesMillis = emptyList(),
    )

    val maya = person("maya", "Maya", "🌻", "peach", "Tending gardens since spring")
    val theo = person("theo", "Theo", "🐢", "sky", "Slow and steady")
    val ana = person("ana", "Ana", "🦊", "lavender", "Phone-free evenings")
    val robin = person("robin", "Robin", "🦔", "honey", "Reading more, scrolling less 📚")
        .copy(approverIds = listOf(maya.uid, theo.uid), approveeIds = listOf(ana.uid))

    /** Robin's day so far, as it's shared with their Bumblebees. */
    val today = SharedUsage(
        Days.todayKey(),
        totalMinutes = 134,
        apps = listOf(
            SharedAppUsage("demo.youtube", "YouTube", 52),
            SharedAppUsage("demo.instagram", "Instagram", 47),
            SharedAppUsage("demo.maps", "Maps", 21),
        ),
    )

    /** Robin's own limits. */
    val myLimits = listOf(AppLimit("demo.youtube", "YouTube", 60, 0), AppLimit("demo.instagram", "Instagram", 45, 300))

    /** 18% less than last week so far, three calmer weeks running, limits kept five days of seven. */
    val progress = Progress(weekChange = -18, calmerWeeks = 3, keptDays = 5, limitDays = 7)

    fun budget(pkg: String, name: String, daily: Int, usedToday: Int, weekly: Int = 0, usedWeek: Int = usedToday * 4, bonus: Int = 0) =
        AppBudget(AppLimit(pkg, name, daily, weekly), usedToday * MIN, usedWeek * MIN, bonus, bonus)

    val tiktok = budget("demo.tiktok", "TikTok", daily = 30, usedToday = 30)

    val garden = GardenState(
        budgets = listOf(
            tiktok,
            budget("demo.youtube", "YouTube", daily = 60, usedToday = 52),
            budget("demo.instagram", "Instagram", daily = 45, usedToday = 17),
            budget("demo.games", "Games", daily = 20, usedToday = 12, bonus = 15),
            budget("demo.reddit", "Reddit", daily = 0, usedToday = 25, weekly = 300, usedWeek = 130),
            budget("demo.news", "News", daily = 40, usedToday = 6),
        ).sortedBy { it.remainingMs },
        todayTotalMs = 134 * MIN,
        appsUsed = 9,
        essentialsReady = true,
        loaded = true,
    )

    val stats: StatsUiState = run {
        val from = Days.weekStart(Days.today()).minusWeeks(1)
        val minutes = listOf(168, 151, 139, 127, 104, 96, 82)
        val limits = mapOf(
            "demo.youtube" to AppLimit("demo.youtube", "YouTube", 60, 0),
            "demo.instagram" to AppLimit("demo.instagram", "Instagram", 45, 0),
            "demo.tiktok" to AppLimit("demo.tiktok", "TikTok", 30, 0),
        )
        val apps = listOf("demo.youtube" to "YouTube" to 305, "demo.instagram" to "Instagram" to 231, "demo.tiktok" to "TikTok" to 168,
            "demo.reddit" to "Reddit" to 121, "demo.maps" to "Maps" to 64, "demo.news" to "News" to 49)
            .map { (app, m) -> AppTime(app.first, app.second, m * MIN, limits[app.first]) }
        val selection = Selection(Period.WEEK, -1)
        StatsUiState(
            selection = selection,
            stats = PeriodStats(
                selection = selection,
                from = from,
                to = from.plusDays(6),
                days = minutes.mapIndexed { i, m -> from.plusDays(i.toLong()) to m * MIN },
                apps = apps,
                totalMs = minutes.sum() * MIN,
                previousTotalMs = 1_090 * MIN,
            ),
            hasPermission = true,
        )
    }

    /** Last month, day by day, for the calendar and the month's chart. */
    val monthStats: StatsUiState = run {
        val from = Days.today().withDayOfMonth(1).minusMonths(1)
        val days = (0 until from.lengthOfMonth()).map { from.plusDays(it.toLong()) }
        // From quiet days to the busiest, so every shade of the calendar is drawn.
        val minutes = days.indices.map { i -> if (i % 9 == 4) 0 else 30 + (i * 37) % 190 }
        val selection = Selection(Period.MONTH, -1)
        StatsUiState(
            selection = selection,
            stats = PeriodStats(
                selection = selection,
                from = from,
                to = days.last(),
                days = days.zip(minutes) { day, m -> day to m * MIN },
                apps = stats.stats!!.apps,
                totalMs = minutes.sum() * MIN,
                previousTotalMs = 3_900 * MIN,
            ),
            hasPermission = true,
        )
    }

    val chat = Chat(
        id = "maya_robin",
        participants = listOf(maya.uid, robin.uid),
        names = mapOf(maya.uid to maya.displayName, robin.uid to robin.displayName),
        avatars = mapOf(maya.uid to maya.avatar, robin.uid to robin.avatar),
        lastMessage = "Thank you! 💛",
        lastMessageAtMillis = now - 2 * MIN,
        lastSenderId = robin.uid,
        lastReadMillis = emptyMap(),
    )

    val request = LimitRequest(
        id = "r1", requesterId = robin.uid, requesterName = robin.displayName, type = RequestType.EXTRA_TIME,
        packageName = "demo.youtube", appName = "YouTube", minutes = 15, dailyMinutes = 0, weeklyMinutes = 0,
        message = "The cooking video I'm following is 12 minutes 🍝", status = RequestStatus.APPROVED,
        approverIds = listOf(maya.uid, theo.uid), decidedByName = maya.displayName, decisionNote = "Enjoy dinner!",
        dayKey = Days.todayKey(), createdAtMillis = now - 20 * MIN,
    )

    private fun message(id: String, from: UserProfile, text: String, minsAgo: Int, kind: MessageKind = MessageKind.TEXT, requestId: String? = null) =
        ChatMessage(id, from.uid, text, kind, requestId, now - minsAgo * MIN, pending = false)

    val messages = listOf(
        message("m1", maya, "How's the reading going? 📚", 95),
        message("m2", robin, "Two chapters today, and no doomscrolling before bed!", 90),
        message("m3", maya, "That's wonderful. Proud of you 🌼", 88),
        message("m4", robin, "", 20, MessageKind.REQUEST, requestId = request.id),
        message("m5", maya, "🌸 Maya approved 15 more minutes of YouTube", 15, MessageKind.SYSTEM),
        message("m6", robin, "Thank you! 💛", 2),
    )

    val rest = RestState(appName = "TikTok", budget = tiktok, signedIn = true, hasBumblebees = true, pendingRequest = null)

    /** Ana asking Robin, her Bumblebee, for a little more time. */
    val askFromAna = request.copy(
        id = "r2", requesterId = ana.uid, requesterName = ana.displayName, requesterFlower = ana.avatar.emoji,
        appName = "Instagram", packageName = "demo.instagram", minutes = 10, message = "Sharing photos from the hike 🏔️",
        status = RequestStatus.PENDING, approverIds = listOf(robin.uid), decidedByName = null, decisionNote = null,
        createdAtMillis = now - 4 * MIN,
    )

    /** Robin's own question, still waiting. */
    val waitingAsk = request.copy(
        id = "r3", packageName = "demo.tiktok", appName = "TikTok", status = RequestStatus.PENDING,
        decidedByName = null, decisionNote = null, message = "", createdAtMillis = now - 6 * MIN,
    )

    val inviteToRobin = Invite("i1", ana.uid, ana.displayName, ana.avatar.emoji, robin.uid, robin.displayName, InviteStatus.PENDING, now - 30 * MIN)
    val inviteFromRobin = Invite("i2", robin.uid, robin.displayName, robin.avatar.emoji, theo.uid, theo.displayName, InviteStatus.PENDING, now - 90 * MIN)

    /** Ana's latest, not read yet. */
    val chatWithAna = Chat(
        id = "ana_robin",
        participants = listOf(ana.uid, robin.uid),
        names = mapOf(ana.uid to ana.displayName, robin.uid to robin.displayName),
        avatars = mapOf(ana.uid to ana.avatar, robin.uid to robin.avatar),
        lastMessage = "Made it to the top! 🏔️",
        lastMessageAtMillis = now - 3 * MIN,
        lastSenderId = ana.uid,
        lastReadMillis = emptyMap(),
    )

    /** Robin's circle: two Bumblebees, Ana's garden, Ana's question and Robin's own. */
    val people = People(
        bumblebees = listOf(maya, theo),
        lookingAfter = listOf(ana),
        chats = listOf(chat, chatWithAna),
        incomingRequests = listOf(askFromAna),
        outgoingRequests = listOf(waitingAsk),
    )

    val beeStats = BeeStats(
        asked = 14, yes = 9, no = 4, missed = 1, waiting = 1, minutesGranted = 135,
        people = listOf(BeeStats.Person(ana.uid, ana.displayName, ana.avatar.emoji, asked = 14, yes = 9, no = 4)),
        leniency = 62,
    )

    /** Ana's page as Robin sees it, with everything shared. */
    val anaPage = SharedPage(
        ana,
        minutes = today.totalMinutes,
        apps = today.apps,
        progress = progress,
        limits = myLimits,
    )
}
