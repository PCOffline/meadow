package dev.pcoffline.meadow.enforcement

import android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import dev.pcoffline.meadow.enforcement.GuardianAccessibilityService.Companion.eventTypesFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Meadow asks Android only for the accessibility events it reads (#106). */
class AccessibilityEventsTest {
    private val windows = TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOWS_CHANGED

    @Test
    fun `without strict mode or a limited site, only window changes are asked for`() {
        assertEquals(windows, eventTypesFor(strict = false, sitesLimited = false))
    }

    @Test
    fun `strict mode reads Settings, so it asks for content changes`() {
        assertEquals(windows or TYPE_WINDOW_CONTENT_CHANGED, eventTypesFor(strict = true, sitesLimited = false))
    }

    @Test
    fun `a limited site reads address bars, so it asks for content changes`() {
        assertEquals(windows or TYPE_WINDOW_CONTENT_CHANGED, eventTypesFor(strict = false, sitesLimited = true))
    }
}
