package dev.pcoffline.meadow.ui.setup

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The notifications step must still lead somewhere after someone refused for good. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotificationStepTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `refused for good, it opens the app's notification settings`() {
        activity.afterNotificationRequest(granted = false, canAskAgain = false)
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened?.action)
        assertEquals(activity.packageName, opened?.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `allowed, or refused once, it leaves the person where they are`() {
        activity.afterNotificationRequest(granted = true, canAskAgain = false)
        activity.afterNotificationRequest(granted = false, canAskAgain = true)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
