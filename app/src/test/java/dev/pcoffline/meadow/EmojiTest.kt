package dev.pcoffline.meadow

import android.app.Application
import android.os.Looper
import android.text.Spanned
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Emoji are drawn with Meadow's own font, on new Android versions too. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class EmojiTest {
    @Test
    fun `on Android 15 and later, emoji are still drawn with Meadow's font`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val emoji = EmojiCompat.reset(MeadowApp.emojiConfig(app))
        val until = System.currentTimeMillis() + 20_000
        while (emoji.loadState == EmojiCompat.LOAD_STATE_LOADING && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals(EmojiCompat.LOAD_STATE_SUCCEEDED, emoji.loadState)
        val text = emoji.process("A daisy 🌼")
        assertTrue("$text", text is Spanned && text.getSpans(0, text.length, EmojiSpan::class.java).isNotEmpty())
    }
}
