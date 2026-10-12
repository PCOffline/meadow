package dev.pcoffline.meadow.showcase

import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.RestState
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.stats.AppTime
import dev.pcoffline.meadow.ui.stats.Period
import dev.pcoffline.meadow.ui.stats.PeriodStats
import dev.pcoffline.meadow.ui.stats.Selection
import dev.pcoffline.meadow.ui.stats.StatsUiState

/** A made-up but lived-in garden for screenshots: Robin, two Bumblebees and a friend. */
object DemoGarden {
    private const val MIN = 60_000L
    private val now = System.currentTimeMillis()

    private fun person(uid: String, name: String, emoji: String, color: String, bio: String = "") = UserProfile(
        uid = uid, displayName = name, avatar = Avatar(emoji, color, null), bio = bio, friendCode = "PETAL7",
        approverIds = emptyList(), approveeIds = emptyList(), privacy = Privacy(), strictMode = true,
        emergencyUntilMillis = 0L, emergencyPackage = null, emergencyUsesMillis = emptyList(),
    )

    val maya = person("maya", "Maya", "🌻", "peach", "Tending gardens since spring")
    val theo = person("theo", "Theo", "🐢", "sky", "Slow and steady")
    val ana = person("ana", "Ana", "🦊", "lavender", "Phone-free evenings")
    val robin = person("robin", "Robin", "🦔", "honey", "Reading more, scrolling less 📚")
        .copy(approverIds = listOf(maya.uid, theo.uid), approveeIds = listOf(ana.uid))

    private fun budget(pkg: String, name: String, daily: Int, usedToday: Int, weekly: Int = 0, usedWeek: Int = usedToday * 4, bonus: Int = 0) =
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
        message("m5", maya, "Maya said yes: 15 more minutes of YouTube 🌼", 15, MessageKind.SYSTEM),
        message("m6", robin, "Thank you! 💛", 2),
    )

    val rest = RestState(appName = "TikTok", budget = tiktok, signedIn = true, hasBumblebees = true, pendingRequest = null)
}
