package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.ui.components.ShareCommitmentHost
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.limits.ShareLimitButton
import org.junit.Test

/** A saved limit has Share in the editor's top bar, at any time. */
class ShareSavedLimitStory : Story("Share a saved limit at any time") {
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun story() {
        val saved = AppLimit("com.instagram.android", "Instagram", dailyMinutes = 30, weeklyMinutes = 0)
        show(firstCaption = "A saved limit has Share at the top.") {
            var unsaved by remember { mutableStateOf(false) }
            Column {
                BackTopBar(text(R.string.limit_editor_existing), onBack = {}) { ShareLimitButton(saved, unsaved) }
                LimitEditor(saved.packageName, LimitEditorState(existing = saved, appName = saved.appName, loaded = true), onDone = {}, onUnsaved = { unsaved = it })
            }
            ShareCommitmentHost()
        }
        step("A change that isn't saved turns Share off.") {
            compose.onAllNodesWithContentDescription(text(R.string.limit_editor_more)).onFirst().performClick()
        }
        step("Undone, Share is on again.") {
            compose.onAllNodesWithContentDescription(text(R.string.limit_editor_less)).onFirst().performClick()
        }
        step("Share opens the share sheet with the limit as saved.") {
            compose.onNodeWithContentDescription(text(R.string.limit_share)).performClick()
        }
    }
}
