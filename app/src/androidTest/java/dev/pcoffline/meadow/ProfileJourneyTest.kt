package dev.pcoffline.meadow

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * End to end: someone makes their profile their own (name, critter, colour, a photo that's later
 * removed), picks the app's theme and night mode in Settings, and downloads all their data. Android's photo picker
 * and file saver are stood in for, answering with files the test made; the rest is the real app
 * against the emulators, checked in the backend.
 */
@RunWith(AndroidJUnit4::class)
class ProfileJourneyTest : EndToEndTest() {
    @Before
    fun standInForAndroidsPickers() = Intents.init()

    @After
    fun releasePickers() = Intents.release()

    /** The backend's photo URL for the profile, or null when there's none. */
    private fun JSONObject.photoUrl(): String? = string("photoUrl")?.takeIf { it.isNotEmpty() }

    @Test
    fun aProfileGetsANewNameAvatarAndPhotoAndTheAppANewTheme() {
        val photo = File(app.cacheDir, "e2e-photo.png").apply {
            outputStream().use { createBitmap(64, 48).apply { eraseColor(Color.YELLOW) }.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        intending(hasAction(MediaStore.ACTION_PICK_IMAGES)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(photo))))

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Robin")
            val uid = FirebaseAuth.getInstance().currentUser!!.uid
            // Every tab has the way to the profile, in the same corner.
            node(R.string.tab_growth).performClick()
            compose.onNodeWithContentDescription(text(R.string.profile_open)).performClick()
            waitFor(R.string.profile_title)

            // A new name, an owl on honey, saved together.
            compose.onNode(hasSetTextAction() and hasText(text(R.string.onboarding_name_label))).performTextReplacement("Robin Hood")
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🦉"))).performScrollTo().performClick()
            compose.onNode(hasContentDescription(text(R.string.avatar_color_honey))).performScrollTo().performClick()
            node(R.string.action_save).performScrollTo().performClick()
            waitFor(R.string.profile_saved)
            waitForBackend("users/$uid") {
                it.string("displayName") == "Robin Hood" && it.string("flower") == "🦉" && it.string("avatarColor") == "honey"
            }
            waitForSnackbarToGo(R.string.profile_saved)

            // A photo, uploaded, then taken down again.
            node(R.string.profile_photo_choose).performScrollTo().performClick()
            waitFor(R.string.profile_photo_saved)
            waitForBackend("users/$uid") { it.photoUrl()?.contains(uid) == true }
            // The message covers the bottom of the screen, where the buttons below will be.
            waitForSnackbarToGo(R.string.profile_photo_saved)
            node(R.string.profile_photo_remove).performScrollTo().performClick()
            waitForBackend("users/$uid") { it.photoUrl() == null }

            // The theme and night mode are in Settings; they apply at once and follow the account to other phones.
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            node(R.string.tab_garden).performClick()
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            waitFor(R.string.settings_appearance_title)
            compose.onNode(hasText(text(R.string.theme_lavender), substring = true) and hasClickAction()).performScrollTo().performClick()
            node(R.string.dark_mode_dark).performScrollTo().performClick()
            waitForBackend("users/$uid/private/account") { it.string("theme") == "lavender" && it.string("darkMode") == "dark" }
            assertEquals("lavender", AppGraph.store.theme.value)
            assertEquals("dark", AppGraph.store.darkMode.value)
        }
    }

    @Test
    fun allMyDataDownloadsAsOneFile() {
        val saved = File(app.cacheDir, "meadow-data.zip").apply { delete() }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(saved))))

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Robin")
            compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
            node(R.string.settings_export).performScrollTo().performClick()
            waitFor(R.string.settings_export_done)
            val data = ZipFile(saved).use { zip -> zip.getInputStream(zip.getEntry("meadow-data.json")).readBytes().decodeToString() }
            JSONObject(data)
            assertTrue("The download doesn't have the profile in it: ${data.take(500)}", "Robin" in data)
        }
    }
}
