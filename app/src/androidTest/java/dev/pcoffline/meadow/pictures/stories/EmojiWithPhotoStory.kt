package dev.pcoffline.meadow.pictures.stories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Photos
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.Badges
import dev.pcoffline.meadow.ui.MeadowTabBar
import dev.pcoffline.meadow.ui.Tab
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import java.io.File
import org.junit.After
import org.junit.Test

/**
 * With a profile photo, the emoji is still a choice. It shows after the name: in the garden's
 * greeting, on People and at the top of a chat.
 */
class EmojiWithPhotoStory : Story("Pick an emoji when you have a profile photo") {
    private enum class Screen { PROFILE, GARDEN, PEOPLE, CHAT }

    private var screen by mutableStateOf(Screen.PROFILE)

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /**
     * A photo of a field under [sky], drawn on the phone: this build has no Storage to load one from.
     * Returns its address, which [Photos] then loads.
     */
    private fun photo(name: String, sky: Int, hill: Int): String {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val gradient = Paint().apply { shader = LinearGradient(0f, 0f, 0f, size * 0.65f, sky, 0xFFF4FBFF.toInt(), Shader.TileMode.CLAMP) }
            drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradient)
            drawCircle(size * 0.72f, size * 0.28f, size * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() })
            drawCircle(size * 0.3f, size * 1.25f, size * 0.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hill })
            drawCircle(size * 0.85f, size * 1.3f, size * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF558B2F.toInt() })
        }
        val file = File(compose.activity.cacheDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString().also { Photos.picturesPhotos += it }
    }

    @After
    fun forgetThePhotos() {
        Photos.picturesPhotos.clear()
    }

    /** A tab's screen with the tab bar under it, as in the app. */
    @Composable
    private fun WithTabs(tab: Tab, content: @Composable () -> Unit) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { MeadowTabBar(tab, Badges()) {} },
        ) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() } }
    }

    @Test
    fun story() {
        val robinPhoto = photo("robin", 0xFF7EC8F2.toInt(), 0xFF7CB342.toInt())
        val mayaPhoto = photo("maya", 0xFFF2B8A0.toInt(), 0xFF9CCC65.toInt())
        val robin = DemoGarden.robin.copy(avatar = DemoGarden.robin.avatar.copy(photoUrl = robinPhoto))
        // Saved with 🐝, as the profile's last step leaves it.
        val robinWithBee = robin.copy(avatar = robin.avatar.copy(emoji = "🐝"))
        val maya = DemoGarden.maya.copy(avatar = DemoGarden.maya.avatar.copy(photoUrl = mayaPhoto))
        val chat = DemoGarden.chat.copy(avatars = DemoGarden.chat.avatars + (maya.uid to maya.avatar))

        show(firstCaption = "A profile with a photo.") {
            when (screen) {
                Screen.PROFILE -> ProfileScreen(robin, onBack = {}, onSave = {})
                Screen.GARDEN -> WithTabs(Tab.GARDEN) { GardenContent(robinWithBee, DemoGarden.garden) }
                Screen.PEOPLE -> WithTabs(Tab.PEOPLE) { PeopleContent(robinWithBee, People(bumblebees = listOf(maya))) }
                Screen.CHAT -> Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    ChatContent(robinWithBee, chat, DemoGarden.messages, requestCard = { _, _ -> })
                }
            }
        }
        step("The emoji is still a choice. It shows next to your name, and there's no circle colour.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🐝"))).performScrollTo()
        }
        step("Picking 🐝 keeps the photo.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🐝"))).performClick()
        }
        step("Saved: the garden's greeting ends with 🐝, after your name.") { screen = Screen.GARDEN }
        step("People: Maya has a photo, so her emoji 🌻 follows her name.") { screen = Screen.PEOPLE }
        step("A chat with Maya: her emoji follows her name at the top.") { screen = Screen.CHAT }
    }
}
