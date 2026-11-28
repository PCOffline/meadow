package dev.pcoffline.meadow.pictures.stories

import androidx.compose.ui.test.hasText
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** Types [keys] for the daily limit, which reads [now], on the keypad. */
    private fun typeDaily(now: String, vararg keys: String) {
        compose.onNodeWithContentDescription("${text(R.string.limit_editor_daily)} $now").performClick()
        keys.forEach { compose.onNode(hasText(it)).performClick() }
        compose.onNode(hasText(text(R.string.action_ok))).performClick()
    }

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
            typeDaily("30m", "4", "5")
        }
        step("Undone, Share is on again.") {
            typeDaily("45m", "3", "0")
        }
        step("Share opens the share sheet with the limit as saved.") {
            compose.onNodeWithContentDescription(text(R.string.limit_share)).performClick()
        }
    }
}
