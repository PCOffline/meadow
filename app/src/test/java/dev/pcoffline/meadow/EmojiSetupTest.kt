package dev.pcoffline.meadow

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Emoji come from Meadow's bundled font, so the downloadable one must not be set up first. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EmojiSetupTest {
    @Test
    fun `the downloadable emoji font isn't set up at startup`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        // Other libraries still start through it, so it's there; only emoji's entry is gone.
        val startup = app.packageManager.getProviderInfo(
            ComponentName(app, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )
        assertNotNull(startup.metaData)
        assertFalse(startup.metaData.containsKey("androidx.emoji2.text.EmojiCompatInitializer"))
    }
}
