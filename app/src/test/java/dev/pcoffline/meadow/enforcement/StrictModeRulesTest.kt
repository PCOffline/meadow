package dev.pcoffline.meadow.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeRulesTest {
    private fun blocks(pkg: String, text: String) = StrictModeRules.shouldBlock(pkg, text.lowercase(), appLabel = "meadow")

    @Test
    fun `blocks Meadow's app info and accessibility toggle`() {
        assertTrue(blocks("com.android.settings", "Meadow\nForce stop\nUninstall"))
        assertTrue(blocks("com.android.settings", "Use Meadow garden keeper"))
        assertTrue(blocks("com.google.android.packageinstaller", "Do you want to uninstall Meadow?"))
    }

    @Test
    fun `blocks changing the clock`() {
        assertTrue(blocks("com.android.settings", "Date & time\nSet time"))
    }

    @Test
    fun `works in the phone's language, using Settings' own words`() {
        val spanish = listOf("forzar detención", "desinstalar")
        assertTrue(StrictModeRules.shouldBlock("com.android.settings", "meadow\nforzar detención", "meadow", spanish))
        assertFalse(StrictModeRules.shouldBlock("com.android.settings", "instagram\nforzar detención", "meadow", spanish))
    }

    @Test
    fun `leaves other settings alone`() {
        assertFalse(blocks("com.android.settings", "Instagram\nForce stop\nUninstall"))
        assertFalse(blocks("com.android.settings", "Apps\nMeadow\nInstagram"))
        assertFalse(blocks("com.android.settings", "Wi-Fi\nBluetooth"))
    }
}
