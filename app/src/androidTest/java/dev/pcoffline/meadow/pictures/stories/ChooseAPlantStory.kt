package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.ui.components.flora.namedPlace
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.theme.GardenTheme
import org.junit.Test

/** The limit editor has a Plant card: a person chooses the plant of the limit from the theme's five. */
class ChooseAPlantStory : Story("Choose the plant of a limit") {
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun story() {
        val saved = AppLimit("com.instagram.android", "Instagram", dailyMinutes = 30, weeklyMinutes = 0)
        val plants = plantsOf(GardenTheme.MEADOW)
        val other = plants[(namedPlace(saved.packageName) + 2) % plants.size]
        show(firstCaption = "A saved limit shows its plant next to the app's name.") {
            Column {
                BackTopBar(text(R.string.limit_editor_existing), onBack = {})
                LimitEditor(
                    saved.packageName, LimitEditorState(existing = saved, appName = saved.appName, loaded = true), onDone = {},
                    save = { _, _, _ -> ChangeOutcome.APPLIED },
                )
            }
        }
        step("The Plant card shows the theme's five plants. The plant of the limit has a ring.") {
            compose.onNodeWithText(text(R.string.limit_editor_plant_title)).performScrollTo()
            compose.onNodeWithContentDescription(plants.last().name).performScrollTo()
        }
        step("A tap chooses the ${other.name.lowercase()}. Save is on.") {
            compose.onNodeWithContentDescription(other.name).performClick()
        }
        step("The plant next to the app's name is the ${other.name.lowercase()} too.") {
            compose.onNodeWithText(saved.appName).performScrollTo()
        }
    }
}
