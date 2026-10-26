package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A double tap acts once, but a different button right after works straight away. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ActionStateTest : ScreenTest() {

    @Test
    fun `a double tap acts once and another button right after still acts`() {
        val done = mutableListOf<String>()
        compose.setContent {
            MeadowTheme {
                SnackbarScope {
                    val action = rememberAction()
                    Column {
                        Button(onClick = { action.run { done += "photo" } }) { Text("Use a photo") }
                        Button(onClick = { action.run { done += "removed" } }) { Text("Remove photo") }
                    }
                }
            }
        }
        compose.onNodeWithText("Use a photo").performClick()
        compose.onNodeWithText("Use a photo").performClick()
        compose.onNodeWithText("Remove photo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("photo", "removed"), done)
    }
}
