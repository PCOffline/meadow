package dev.pcoffline.meadow

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The links Meadow sends people open the app, on the domains Firebase actually uses. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LinkRoutingTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun opens(url: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }

    @Test
    fun `invite links open the app`() {
        assertEquals(listOf(MainActivity::class.java.name), opens("https://${BuildConfig.LINK_HOST}/i/abc123"))
    }

    @Test
    fun `sign-in links open the app on the firebaseapp domain`() {
        // Firebase Auth sends email links through <project>.firebaseapp.com when no custom domain is set.
        val link = "https://${BuildConfig.AUTH_LINK_HOST}/__/auth/links?link=https%3A%2F%2Fexample.invalid"
        assertEquals(listOf(MainActivity::class.java.name), opens(link))
    }

    @Test
    fun `other paths stay in the browser`() {
        assertEquals(emptyList<String>(), opens("https://${BuildConfig.LINK_HOST}/privacy"))
        assertEquals(emptyList<String>(), opens("https://${BuildConfig.AUTH_LINK_HOST}/i/abc123"))
    }
}
