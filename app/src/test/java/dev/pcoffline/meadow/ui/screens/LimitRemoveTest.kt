package dev.pcoffline.meadow.ui.screens

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.limits.AppPickerScreen
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Choosing an app to limit, and removing a limit alone. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LimitRemoveTest : ScreenTest() {
    private val pkg = "com.instagram.android"
    private val limit = AppLimit(pkg, "Instagram", dailyMinutes = 30, weeklyMinutes = 0)

    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun editor(bees: Int) = show {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(pkg, LimitEditorState(existing = limit, appName = "Instagram", bumblebeeCount = bees, loaded = true), onDone = {})
        }
    }

    private fun inDialog(id: Int) = compose.onNode(hasText(text(id)) and hasAnyAncestor(isDialog()))

    /** Installs an app that the launcher shows, named [label]. */
    private fun install(packageName: String, label: String) {
        val pm = shadowOf(app.packageManager)
        val info = ApplicationInfo().apply { this.packageName = packageName; nonLocalizedLabel = label }
        pm.installPackage(PackageInfo().apply { this.packageName = packageName; applicationInfo = info })
        val main = ComponentName(packageName, "$packageName.Main")
        pm.addOrUpdateActivity(ActivityInfo().apply { this.packageName = packageName; name = main.className; applicationInfo = info })
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun `alone, removing a limit asks once and can be kept`() {
        editor(bees = 0)
        node(R.string.limit_editor_remove).performScrollTo().performClick()
        node(R.string.limit_remove_title, "Instagram").assertExists()
        node(R.string.limit_remove_body_now).assertExists()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
        assertAccessible(requireHeading = false)

        inDialog(R.string.action_keep).performClick()
        node(R.string.limit_remove_title, "Instagram").assertDoesNotExist()
    }

    @Test
    fun `a removal that can't reach the garden says so`() {
        // Nobody is signed in, so there is no garden to remove the limit from.
        editor(bees = 0)
        node(R.string.limit_editor_remove).performScrollTo().performClick()
        inDialog(R.string.action_remove).performClick()
        node(R.string.limit_remove_title, "Instagram").assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("🍂", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the apps to choose from say how long each was used today, and which have a limit`() {
        install("com.example.garden", "Garden Notes")
        install("com.example.chat", "Chatter")
        AppGraph.catalog.invalidate()
        AppGraph.store.setLimits(listOf(AppLimit("com.example.chat", "Chatter", dailyMinutes = 20, weeklyMinutes = 0)))
        val picked = mutableListOf<String>()
        show { AppPickerScreen(onBack = {}, onPick = { picked += it }) }

        compose.waitUntil(5_000) { shows("Garden Notes") }
        assertEquals(2, compose.onAllNodesWithText(text(R.string.picker_not_opened)).fetchSemanticsNodes().size)
        // Only the app with a limit is planted.
        assertEquals(1, compose.onAllNodesWithText(text(R.string.picker_planted)).fetchSemanticsNodes().size)
        assertAccessible()

        compose.onNode(hasSetTextAction()).performTextInput("garden")
        compose.waitUntil(5_000) { !shows("Chatter") }
        compose.onNodeWithText("Garden Notes").performClick()
        assertEquals(listOf("com.example.garden"), picked)
    }
}
