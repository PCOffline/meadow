package dev.pcoffline.meadow.pictures.stories

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import dev.pcoffline.meadow.pictures.Device
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.home.GardenContent
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * The garden in landscape, with a camera cutout on one side: the scene reaches both edges, and
 * the words and cards keep clear of the cutout and the navigation bar.
 */
class LandscapeSceneStory : Story("In landscape, the garden's scene reaches both sides") {
    @Test
    fun story() {
        show(firstCaption = "Landscape: the sky and the hills reach the cutout and the far side. The words keep clear of the cutout.") {
            GardenContent(DemoGarden.robin, DemoGarden.garden)
        }
        step("Scrolled down: the cards keep clear of both sides too.") {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }
    }

    companion object {
        private const val CUTOUT = "com.android.internal.display.cutout.emulation.tall"
        private val drawing get() = InstrumentationRegistry.getArguments().getString("pictures") == "true"

        /** Before the activity starts: the phone turns to landscape, with a tall cutout. */
        @JvmStatic
        @BeforeClass
        fun turnToLandscape() {
            if (!drawing) return
            Device.shell("cmd overlay enable-exclusive --category $CUTOUT")
            Device.shell("settings put system accelerometer_rotation 0")
            Device.shell("settings put system user_rotation 1")
            // The cutout and the rotation take a moment to apply.
            Thread.sleep(3_000)
        }

        /** The phone as the other stories expect it: portrait, with no cutout. */
        @JvmStatic
        @AfterClass
        fun turnBack() {
            if (!drawing) return
            Device.shell("settings put system user_rotation 0")
            Device.shell("cmd overlay disable $CUTOUT")
            Thread.sleep(3_000)
        }
    }
}
