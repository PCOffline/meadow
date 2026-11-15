package dev.pcoffline.meadow.pictures.stories

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.Weather
import dev.pcoffline.meadow.pictures.Device
import dev.pcoffline.meadow.ui.LocalNow
import dev.pcoffline.meadow.ui.SnackbarScope
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.components.LocalSkyWeather
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * A pull request's story: a change shown step by step, as a person meets it. The Pictures
 * workflow runs each story on the pull request and on its base, and puts each step's screenshot,
 * its caption and a video of the whole story in the pull request. AGENTS.md says when a pull
 * request needs one.
 *
 * Write one class for each story, in this package. Give it a [title], [show] the screen, then
 * call [step] for each thing a person does:
 *
 * ```
 * class WeekdayHoursStory : Story("Set hours for several weekdays at once") {
 *     @Test fun story() {
 *         show { ScheduleEditorDemo() }
 *         step("Per weekday shows the days to set.") { node("Per weekday").performClick() }
 *         step("Saturday and Sunday are picked together.") { ... }
 *     }
 * }
 * ```
 *
 * The motion is on, so the video shows animations. Skipped unless run with
 * `-Pandroid.testInstrumentationRunnerArguments.pictures=true`.
 */
@RunWith(AndroidJUnit4::class)
abstract class Story(private val title: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val dir get() = "stories/${javaClass.simpleName}"
    private val captions = mutableListOf<String>()
    private var recording: ParcelFileDescriptor? = null

    @Before
    fun setUpStory() {
        Device.requirePictures()
        Device.prepare(animations = true)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    /**
     * Shows [content] as the app does, in [theme] and [mode], with the sky at 10:00 under a clear
     * sky and the clock at [Device.now], and starts the video. The first step's picture is the
     * screen as [content] first draws it.
     */
    protected fun show(
        theme: GardenTheme = GardenTheme.MEADOW,
        mode: DarkMode = DarkMode.LIGHT,
        firstCaption: String = "The screen as it opens.",
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            MeadowTheme(theme, mode) {
                CompositionLocalProvider(LocalSkyHour provides 10, LocalSkyWeather provides Weather.CLEAR, LocalNow provides { Device.now }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SnackbarScope(content) }
                }
            }
        }
        recording = Device.startRecording("$dir/story.mp4", seconds = MAX_SECONDS)
        settle()
        capture(firstCaption)
    }

    /** Does [action], then takes the step's picture with [caption] under it. */
    protected fun step(caption: String, action: () -> Unit) {
        action()
        settle()
        capture(caption)
    }

    private fun settle() {
        compose.waitForIdle()
        // Long enough for the motion that a tap starts to end, and for the video to show it.
        Thread.sleep(SETTLE_MS)
    }

    private fun capture(caption: String) {
        captions += caption
        Device.screenshot("$dir/%02d.png".format(captions.size))
    }

    @After
    fun finishStory() {
        recording?.let(Device::stopRecording)
        if (captions.isEmpty()) return
        val steps = captions.joinToString(",") { "\"${escape(it)}\"" }
        Device.write("$dir/story.json", "{\"title\": \"${escape(title)}\", \"steps\": [$steps]}")
    }

    private companion object {
        /** The longest video Android records. */
        const val MAX_SECONDS = 180
        const val SETTLE_MS = 900L

        fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
