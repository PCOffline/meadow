package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.LimitCardMode
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.data.SharedAppUsage
import dev.pcoffline.meadow.data.SharedPage
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.limits.LimitMenuActions
import dev.pcoffline.meadow.ui.limitText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sharing, copying and suggesting limits (#93). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class LimitSharingTest : ScreenTest() {
    private val robin = DemoGarden.robin
    private val maya = DemoGarden.maya
    private val site = AppLimit("site:youtube.com", "youtube.com", 45, 0)

    private fun card(id: String, from: String, limit: AppLimit, mode: LimitCardMode) =
        ChatMessage(id, from, "", MessageKind.LIMIT, null, System.currentTimeMillis(), pending = false, limit = limit, limitMode = mode)

    private fun chat(vararg cards: ChatMessage, onCopy: (AppLimit) -> Unit = {}) = show {
        ChatContent(robin, DemoGarden.chat, cards.toList(), requestCard = { id, _ -> Text("Request $id") }, onCopyLimit = onCopy)
    }

    @After
    fun noLimits() = AppGraph.store.setLimits(emptyList())

    @Test
    fun `a shared limit shows the whole limit, and the person it was sent to can copy it`() {
        val copied = mutableListOf<AppLimit>()
        chat(card("c1", maya.uid, site, LimitCardMode.SHARED), onCopy = { copied += it })
        node(R.string.limit_card_shared).assertExists()
        compose.onNodeWithText(app.resources.limitText(site)).assertExists()
        node(R.string.limit_menu_copy).performScrollTo().performClick()
        assertEquals(listOf(site), copied)
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `my own card has no buttons`() {
        chat(card("c1", robin.uid, site, LimitCardMode.SUGGESTED))
        node(R.string.limit_card_suggested).assertExists()
        node(R.string.limit_apply).assertDoesNotExist()
        node(R.string.limit_change).assertDoesNotExist()
    }

    @Test
    fun `Change on a suggested limit opens it to change first`() {
        val copied = mutableListOf<AppLimit>()
        chat(card("c1", maya.uid, site, LimitCardMode.SUGGESTED), onCopy = { copied += it })
        node(R.string.limit_apply).assertIsEnabled()
        node(R.string.limit_change).performScrollTo().performClick()
        assertEquals(listOf(site), copied)
    }

    @Test
    fun `Apply over a limit I have shows what changes first`() {
        val mine = site.copy(dailyMinutes = 30)
        AppGraph.store.setLimits(listOf(mine))
        chat(card("c1", maya.uid, site, LimitCardMode.SUGGESTED))
        node(R.string.limit_apply).performScrollTo().performClick()
        node(R.string.limit_apply_title).assertExists()
        node(R.string.limit_apply_now).assertExists()
        node(R.string.limit_apply_after).assertExists()
        compose.onNodeWithText(app.resources.limitText(mine)).assertExists()
        assertAccessible(requireHeading = false)
        node(R.string.action_cancel).performClick()
        node(R.string.limit_apply_title).assertDoesNotExist()
    }

    @Test
    fun `Apply for an app that isn't on this phone saves nothing and says so`() {
        val elsewhere = AppLimit("com.not.installed", "Elsewhere", 30, 0)
        // With a snackbar host that draws, so its words can be found.
        compose.setContent {
            dev.pcoffline.meadow.ui.theme.MeadowTheme {
                dev.pcoffline.meadow.ui.SnackbarScope {
                    ChatContent(robin, DemoGarden.chat, listOf(card("c1", maya.uid, elsewhere, LimitCardMode.SUGGESTED)), requestCard = { id, _ -> Text("Request $id") })
                }
            }
        }
        node(R.string.limit_apply).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.limit_apply_not_on_phone, "Elsewhere"))).fetchSemanticsNodes().isNotEmpty() }
        node(R.string.limit_apply_title).assertDoesNotExist()
        assertAccessible(requireHeading = false)
    }

    private val usage = listOf(SharedAppUsage("demo.youtube", "YouTube", 50), SharedAppUsage("demo.reader", "Reader", 20))
    private val theirLimit = AppLimit("demo.youtube", "YouTube", 60, 0)

    private fun page(menu: LimitMenuActions?) = show {
        Column { FriendPage(SharedPage(maya, 70, usage, null, listOf(theirLimit)), limitMenu = menu) }
    }

    private fun opensMenu(name: String) = SemanticsMatcher("opens the limit menu for $name") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == text(R.string.limit_menu_open, name)
    }

    @Test
    fun `on a person's page, a hint says limits do more, and a limit's menu copies or suggests a change`() {
        val copied = mutableListOf<AppLimit>()
        val suggested = mutableListOf<Triple<String, String, AppLimit?>>()
        page(LimitMenuActions(onCopy = { copied += it }, onSuggest = { p, n, c -> suggested += Triple(p, n, c) }))
        node(R.string.limit_menu_hint).assertExists()
        // On their limits card (the last of YouTube's two rows).
        // YouTube is in their top apps and in their limits; this is its row in the limits card.
        compose.onAllNodes(opensMenu("YouTube")).onLast().performClick()
        assertAccessible()
        node(R.string.limit_menu_copy).performClick()
        assertEquals(listOf(theirLimit), copied)
        compose.onAllNodes(opensMenu("YouTube")).onLast().performClick()
        node(R.string.limit_menu_suggest_change).performClick()
        assertEquals(listOf(Triple("demo.youtube", "YouTube", theirLimit)), suggested)
        assertAccessible()
    }

    @Test
    fun `an app in their usage with no limit can only have one suggested`() {
        val suggested = mutableListOf<Triple<String, String, AppLimit?>>()
        page(LimitMenuActions(onCopy = {}, onSuggest = { p, n, c -> suggested += Triple(p, n, c) }))
        compose.onNode(opensMenu("Reader")).performClick()
        node(R.string.limit_menu_copy).assertDoesNotExist()
        node(R.string.limit_menu_suggest_new).performClick()
        assertEquals(listOf(Triple("demo.reader", "Reader", null)), suggested)
    }

    @Test
    fun `my own page, as a preview, has no menu and no hint`() {
        page(null)
        node(R.string.limit_menu_hint).assertDoesNotExist()
        compose.onNode(opensMenu("YouTube")).assertDoesNotExist()
    }

    private val pkg = "com.instagram.android"

    @Test
    fun `a copy starts from the copied limit, and says it replaces mine`() {
        val mine = AppLimit(pkg, "Instagram", 30, 0)
        show {
            LimitEditor(pkg, LimitEditorState(existing = mine, appName = "Instagram", loaded = true), onDone = {}, start = mine.copy(dailyMinutes = 45), onChangeApp = {})
        }
        node(R.string.limit_copy_replaces, "Instagram").assertExists()
        node(R.string.limit_copy_other_app).assertExists()
        assertTrue("starts at 45m", compose.onAllNodes(hasText("45m")).fetchSemanticsNodes().isNotEmpty())
        node(R.string.action_save).performScrollTo().assertIsEnabled()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a copy for an app that isn't on this phone says so, and can't be saved until another app is chosen`() {
        val elsewhere = AppLimit("com.not.installed", "Elsewhere", 30, 0)
        show {
            LimitEditor(
                elsewhere.packageName, LimitEditorState(appName = "Elsewhere", loaded = true), onDone = {},
                start = elsewhere, onChangeApp = {}, notHere = true,
            )
        }
        node(R.string.limit_apply_not_on_phone, "Elsewhere").assertExists()
        node(R.string.limit_copy_other_app).assertExists()
        node(R.string.limit_editor_plant).performScrollTo().assertIsNotEnabled()
        assertAccessible(requireHeading = false)
    }

    @Test
    fun `a suggestion has only Send, and sends the limit as written`() {
        val sent = mutableListOf<AppLimit>()
        show {
            LimitEditor(
                pkg, LimitEditorState(appName = "Instagram", loaded = true, bumblebeeCount = 1), onDone = {},
                start = AppLimit(pkg, "Instagram", 45, 0), sendTo = { sent += it },
            )
        }
        node(R.string.action_save).assertDoesNotExist()
        node(R.string.limit_editor_plant).assertDoesNotExist()
        node(R.string.limit_editor_locked_hint).assertDoesNotExist()
        node(R.string.limit_suggest_send).performScrollTo().performClick()
        compose.waitUntil(5_000) { sent.isNotEmpty() }
        assertEquals(45, sent.single().dailyMinutes)
        assertEquals(pkg, sent.single().packageName)
    }

    @Test
    fun `sharing a saved limit keeps the limit, to send it as a card`() {
        dev.pcoffline.meadow.ui.components.ShareCommitment.open("words", site)
        assertEquals(site, dev.pcoffline.meadow.ui.components.ShareCommitment.shared.value?.limit)
        dev.pcoffline.meadow.ui.components.ShareCommitment.close()
    }
}
