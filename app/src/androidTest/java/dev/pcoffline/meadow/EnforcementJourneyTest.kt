package dev.pcoffline.meadow

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.enforcement.GuardianAccessibilityService
import dev.pcoffline.meadow.enforcement.MeadowNotificationListener
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, with the garden keeper (Meadow's accessibility service) really switched on: a
 * resting app opened from the launcher is covered by the block screen, and in strict mode
 * Meadow's own app info, where it could be force stopped, is closed as soon as it opens.
 */
@RunWith(AndroidJUnit4::class)
class EnforcementJourneyTest : EndToEndTest() {
    private val keeper get() = "${app.packageName}/${GuardianAccessibilityService::class.java.name}"

    @After
    fun switchOffTheKeeper() {
        // Phone-wide settings outlast the app's data, so they're undone for the tests after.
        shell("settings delete secure enabled_accessibility_services")
        shell("settings put secure accessibility_enabled 0")
        shell("cmd notification disallow_listener ${app.packageName}/${MeadowNotificationListener::class.java.name}")
        shell("dumpsys deviceidle whitelist -${app.packageName}")
    }

    private fun switchOnTheKeeper() {
        shell("settings put secure enabled_accessibility_services $keeper")
        shell("settings put secure accessibility_enabled 1")
        eventually("the garden keeper running") { GuardianAccessibilityService.running }
    }

    /** The activity on top of the screen, as the system lists it. */
    private fun onTop(): String =
        shell("dumpsys activity activities").lines().firstOrNull { "topResumedActivity" in it || "mResumedActivity" in it }.orEmpty()

    private fun openMeadowsAppInfo() = app.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    @Test
    fun aRestingAppOpenedFromTheLauncherIsCovered() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Kai")
            // Allowed at no hour of any day, so it's resting right now.
            runBlocking { AppGraph.limits.save(AppLimit(pebbles, "Pebbles", 0, 0, Schedule((1..7).associateWith { emptyList() }))) }
            eventually("Pebbles resting") { AppGraph.engine.budget(pebbles, forceRefresh = true)?.exhausted == true }
            switchOnTheKeeper()

            openPebbles()
            waitForText(text(R.string.rest_title, "Pebbles"))
            eventually("Pebbles covered") { "BlockActivity" in onTop() }
            node(R.string.rest_home).performScrollTo().performClick()
            eventually("the block screen gone") { !showing(text(R.string.rest_title, "Pebbles")) }
        }
    }

    @Test
    fun strictModeClosesMeadowsAppInfo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Kai")
            switchOnTheKeeper()

            // Without strict mode, Meadow's app info opens as usual.
            openMeadowsAppInfo()
            eventually("Meadow's app info open") { "com.android.settings" in onTop() }
            shell("input keyevent KEYCODE_BACK")
            eventually("back in Meadow") { "com.android.settings" !in onTop() }

            // In strict mode it's closed as soon as it shows.
            shell("cmd notification allow_listener ${app.packageName}/${MeadowNotificationListener::class.java.name}")
            shell("dumpsys deviceidle whitelist +${app.packageName}")
            eventually("strict mode's protections on") { AppGraph.protection.current().strictReady }
            runBlocking { AppGraph.social.setStrictMode(true) }
            eventually("strict mode on this phone") { AppGraph.store.strictMode.value }
            openMeadowsAppInfo()
            // Time enough for it to open: without strict mode it stayed open (above).
            Thread.sleep(5_000)
            eventually("Meadow's app info closed") { "com.android.settings" !in onTop() }
        }
    }
}
