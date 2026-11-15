package dev.pcoffline.meadow

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Rules that every piece of app text follows (AGENTS.md, "App text"). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppTextTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Each string resource, by name, as written (its placeholders not filled in). */
    private val strings = R.string::class.java.fields.associate { it.name to app.getString(it.getInt(null)) }

    @Test
    fun `an emoji at the end stays on the line of the last word`() {
        // A plain space lets a title wrap before the emoji, which then sits alone on a line.
        val alone = strings.filterValues { Regex(" \\p{So}\\uFE0F?$").containsMatchIn(it) }
        assertTrue("These end with a plain space before the emoji: ${alone.keys}", alone.isEmpty())
    }

    @Test
    fun `strings are read`() {
        assertTrue(strings.size > 300)
    }
}
