package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.minutesText
import org.junit.Test

/** The daily limit's presets with the phone's text at 1.5 times its size. */
class LargeTextPresetsStory : Story("Each preset shows its whole length with large text") {
    @Test
    fun story() {
        show(firstCaption = "Text at 1.5 times its size: each preset shows its whole length.") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * 1.5f)) {
                Column(Modifier.statusBarsPadding()) {
                    LimitEditor("demo.youtube", LimitEditorState(appName = "YouTube", loaded = true), onDone = {})
                }
            }
        }
        step("Tapping 1h 30m picks it, and the time above shows 1h 30m.") {
            val label = compose.activity.resources.minutesText(90)
            compose.onNode(hasText(label) and isSelectable()).performClick()
        }
    }
}
