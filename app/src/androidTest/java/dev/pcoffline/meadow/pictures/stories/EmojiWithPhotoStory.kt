package dev.pcoffline.meadow.pictures.stories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Photos
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.profile.ProfileScreen
import java.io.File
import org.junit.After
import org.junit.Test

/** With a profile photo, the emoji is still a choice, and shows next to the name. */
class EmojiWithPhotoStory : Story("Pick an emoji when you have a profile photo") {
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /** A photo of a sunny field, drawn on the phone: this build has no Storage to load one from. */
    private fun photo(): String {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val sky = Paint().apply { shader = LinearGradient(0f, 0f, 0f, size * 0.65f, 0xFF7EC8F2.toInt(), 0xFFD8F0FF.toInt(), Shader.TileMode.CLAMP) }
            drawRect(0f, 0f, size.toFloat(), size.toFloat(), sky)
            drawCircle(size * 0.72f, size * 0.28f, size * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() })
            drawCircle(size * 0.3f, size * 1.25f, size * 0.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7CB342.toInt() })
            drawCircle(size * 0.85f, size * 1.3f, size * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF558B2F.toInt() })
        }
        val file = File(compose.activity.cacheDir, "robin-photo.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString().also { Photos.picturesPhoto = it }
    }

    @After
    fun forgetThePhoto() {
        Photos.picturesPhoto = null
    }

    @Test
    fun story() {
        val robin = DemoGarden.robin.copy(avatar = DemoGarden.robin.avatar.copy(photoUrl = photo()))
        show(firstCaption = "A profile with a photo.") { ProfileScreen(robin, onBack = {}, onSave = {}) }
        step("The emoji is still a choice. It shows next to your name, and there's no circle colour.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🌻"))).performScrollTo()
        }
        step("Picking 🌻 keeps the photo.") {
            compose.onNode(hasContentDescription(text(R.string.flower_picker_item, "🌻"))).performClick()
        }
    }
}
