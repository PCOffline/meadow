package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.auth.SecureAccountCard
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A typed password must never be written into the saved instance state. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PasswordStateTest : ScreenTest() {

    @Test
    fun `a password is not kept across a restore, the email is`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MeadowTheme { AppBackground { SecureAccountCard(R.string.settings_secure_reason) } } }
        node(R.string.auth_password_instead).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.field_email))).performTextInput("robin@example.com")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.field_password))).performTextInput("hunter22")
        node(R.string.secure_button).assertIsEnabled()

        restoration.emulateSavedInstanceStateRestore()

        // Still on the password form with the email filled in, but the password must be typed again.
        compose.onNode(hasSetTextAction() and hasText("robin@example.com")).assertExists()
        node(R.string.secure_button).assertIsNotEnabled()
    }
}
