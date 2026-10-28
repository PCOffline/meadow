package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Websites: what counts as one, and which address is on which limited site. */
class SitesTest {
    @Test
    fun `what someone types becomes the site's domain`() {
        mapOf(
            "youtube.com" to "youtube.com",
            "  YouTube.com  " to "youtube.com",
            "www.youtube.com" to "youtube.com",
            "https://www.youtube.com/watch?v=abc" to "youtube.com",
            "http://m.YouTube.com:8080/x" to "m.youtube.com",
            "reddit.com/r/android" to "reddit.com",
            "bücher.de" to "xn--bcher-kva.de",
            "news.ycombinator.com." to "news.ycombinator.com",
            "my-site.co.uk" to "my-site.co.uk",
        ).forEach { (typed, domain) -> assertEquals(typed, domain, Sites.normalize(typed)) }
    }

    @Test
    fun `anything that isn't a website's address is turned away`() {
        listOf(
            "", "   ", "youtube", "cats videos", "ftp://youtube.com", "javascript:alert(1)", "file:///sdcard/x.html",
            "chrome://settings", "about:blank", "192.168.1.1", "10.0.0.1:80", "-bad.com", "bad-.com", "you_tube.com",
            "a..com", ".com", "[::1]", "${"a".repeat(64)}.com", "${(1..60).joinToString(".") { "abcd" }}.com",
        ).forEach { assertNull(it, Sites.normalize(it)) }
    }

    @Test
    fun `a site's id is its domain under site, and apps' packages are never sites`() {
        assertEquals("site:youtube.com", Sites.idFor("youtube.com"))
        assertEquals("youtube.com", Sites.domainOf("site:youtube.com"))
        assertTrue(Sites.isSite("site:youtube.com"))
        assertFalse(Sites.isSite("com.google.android.youtube"))
        assertNull(Sites.domainOf("com.google.android.youtube"))
    }

    @Test
    fun `a limit covers every part of its site, and nothing that only looks like it`() {
        assertTrue(Sites.covers("youtube.com", "youtube.com"))
        assertTrue(Sites.covers("youtube.com", "m.youtube.com"))
        assertTrue(Sites.covers("youtube.com", "www.youtube.com"))
        assertFalse(Sites.covers("youtube.com", "notyoutube.com"))
        assertFalse(Sites.covers("youtube.com", "youtube.com.evil.example"))
        assertFalse(Sites.covers("m.youtube.com", "youtube.com"))
    }

    @Test
    fun `the address bar is matched with the limited sites, the most specific first`() {
        val limited = listOf("youtube.com", "news.google.com", "google.com")
        assertEquals("youtube.com", Sites.match("m.youtube.com/watch?v=1", limited))
        assertEquals("youtube.com", Sites.match("https://www.youtube.com", limited))
        assertEquals("news.google.com", Sites.match("news.google.com/topics", limited))
        assertEquals("google.com", Sites.match("google.com/search?q=cats", limited))
        // Searches, other sites and the browser's own pages aren't any limited site.
        listOf("cats videos", "wikipedia.org", "chrome://newtab", "", "youtube.com.evil.example/").forEach {
            assertNull(it, Sites.match(it, limited))
        }
        assertNull(Sites.match("youtube.com", emptyList()))
    }

    @Test
    fun `only known browsers' address bars are read`() {
        assertEquals("com.android.chrome:id/url_bar", Browsers.addressBarOf("com.android.chrome"))
        assertEquals("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", Browsers.addressBarOf("org.mozilla.firefox"))
        assertEquals("com.sec.android.app.sbrowser:id/location_bar_edit_text", Browsers.addressBarOf("com.sec.android.app.sbrowser"))
        assertNull(Browsers.addressBarOf("com.instagram.android"))
        assertNull(Browsers.addressBarOf("dev.pcoffline.meadow.test"))
        assertEquals("dev.pcoffline.meadow.test:id/url_bar", Browsers.addressBarOf("dev.pcoffline.meadow.test", mapOf("dev.pcoffline.meadow.test" to "url_bar")))
    }
}
