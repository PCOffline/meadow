package dev.pcoffline.meadow

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.enforcement.GuardianAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, websites with the garden keeper really on, in a stand-in browser: a site is added
 * by typing its address, only that site is watched (other addresses are let go), its time is
 * measured on the phone, and once it's resting, opening it is covered by the block screen.
 */
@RunWith(AndroidJUnit4::class)
class WebsiteJourneyTest : EndToEndTest() {
    private val keeper get() = "${app.packageName}/${GuardianAccessibilityService::class.java.name}"
    private val site = "site:example.org"

    @After
    fun switchOffTheKeeper() {
        shell("settings delete secure enabled_accessibility_services")
        shell("settings put secure accessibility_enabled 0")
    }

    private fun switchOnTheKeeper() {
        shell("settings put secure enabled_accessibility_services $keeper")
        shell("settings put secure accessibility_enabled 1")
        eventually("the garden keeper running") { GuardianAccessibilityService.running }
    }

    private fun browse(address: String) = app.startActivity(
        Intent().setClassName(pebbles, StandInBrowserActivity::class.java.name)
            .putExtra(StandInBrowserActivity.EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun onTop(): String =
        shell("dumpsys activity activities").lines().firstOrNull { "topResumedActivity" in it || "mResumedActivity" in it }.orEmpty()

    @Test
    fun aWebsiteIsAddedByItsAddressWatchedAloneAndCoveredWhenResting() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Kai")

            // Typing an address offers the website; planting it saves it under its domain.
            node(R.string.garden_empty_title).performScrollTo().performClick()
            waitFor(R.string.picker_title)
            typeInto(hasSetTextAction(), "https://www.Example.org/news")
            waitForText(text(R.string.picker_site, "example.org"))
            compose.onNode(hasText(text(R.string.picker_site, "example.org")) and hasClickAction()).performClick()
            waitFor(R.string.limit_editor_new)
            node(R.string.limit_editor_site_browsers).assertExists()
            node(R.string.limit_editor_plant).performScrollTo().performClick()
            waitFor(R.string.limit_editor_planted)
            eventually("the site's limit on this phone") { AppGraph.store.limits.value.any { it.packageName == site } }
            waitForSnackbarToGo(R.string.limit_editor_planted)
            waitForText("example.org")

            // Another site is let go of: nothing is measured.
            switchOnTheKeeper()
            browse("en.wikipedia.org/wiki/Meadow")
            eventually("the stand-in browser in front") { "StandInBrowserActivity" in onTop() }
            Thread.sleep(2_000)
            assertNull(AppGraph.sites.current())

            // On the limited site, in any part of it, its time is measured.
            browse("https://news.example.org/today")
            eventually("example.org watched") { AppGraph.sites.current() == site }
            eventually("time on example.org", timeout = 30_000) {
                AppGraph.engine.budget(site, forceRefresh = true)!!.usedTodayMs > 0
            }

            // Resting (allowed at no hour), it's covered as soon as it's opened again.
            runBlocking { AppGraph.limits.save(AppLimit(site, "example.org", 30, 0, Schedule((1..7).associateWith { emptyList() }))) }
            eventually("example.org resting") { AppGraph.engine.budget(site, forceRefresh = true)?.exhausted == true }
            browse("example.org")
            waitForText(text(R.string.rest_title, "example.org"))
            eventually("example.org covered") { "BlockActivity" in onTop() }
            node(R.string.rest_home).performScrollTo().performClick()
            try {
                eventually("home") { onTop().let { "BlockActivity" !in it && "StandInBrowserActivity" !in it && it.isNotEmpty() } }
            } catch (e: AssertionError) {
                throw AssertionError("${e.message} On top: ${onTop().trim()} Tappable: ${tappables()} Windows: ${windowsOnScreen()}", e)
            }
            assertEquals(null, AppGraph.sites.current())
        }
    }
}
