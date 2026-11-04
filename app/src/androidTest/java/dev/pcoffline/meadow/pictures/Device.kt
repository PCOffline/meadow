package dev.pcoffline.meadow.pictures

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * The phone side of the pull request pictures (the Pictures workflow): a tidy screen to draw on,
 * and files the workflow pulls from [DIR].
 */
internal object Device {
    /** Where the pictures go. It's shared storage, so clearing the app's data between tests keeps them. */
    const val DIR = "/sdcard/Pictures/meadow-pictures"

    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    /** Skips the test unless the Pictures workflow runs it (`pictures=true`). */
    fun requirePictures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pictures") == "true")
    }

    /**
     * A screen that looks the same on every run: a fixed time, full signal and battery, no
     * notification icons and no "isn't responding" dialogs. [animations] off freezes the ambient
     * motion too, so two runs draw the same pixels.
     */
    fun prepare(animations: Boolean) {
        shell("mkdir -p $DIR")
        shell("settings put global hide_error_dialogs 1")
        shell("am force-stop com.google.android.apps.nexuslauncher")
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        val scale = if (animations) "1" else "0"
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .forEach { shell("settings put global $it $scale") }
        shell("settings put global sysui_demo_allowed 1")
        listOf(
            "enter",
            "clock -e hhmm 0941",
            "battery -e level 100 -e plugged false",
            "network -e wifi show -e level 4",
            "network -e mobile show -e datatype none -e level 4",
            "notifications -e visible false",
        ).forEach { shell("am broadcast -a com.android.systemui.demo -e command $it") }
    }

    /** Saves the whole screen as [path], under [DIR]. */
    fun screenshot(path: String) {
        shell("mkdir -p ${"$DIR/$path".substringBeforeLast('/')}")
        shell("screencap -p $DIR/$path")
    }

    /** Writes [text] to [path], under [DIR]. */
    fun write(path: String, text: String) {
        shell("mkdir -p ${"$DIR/$path".substringBeforeLast('/')}")
        // The shell command runs without a shell, so tee takes the text from its input.
        val (output, input) = automation.executeShellCommandRw("tee $DIR/$path")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(text.toByteArray()) }
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    /** Starts a screen recording to [path], under [DIR], for at most [seconds]. [stopRecording] ends it. */
    fun startRecording(path: String, seconds: Int): ParcelFileDescriptor {
        shell("mkdir -p ${"$DIR/$path".substringBeforeLast('/')}")
        return automation.executeShellCommand("screenrecord --time-limit $seconds --bit-rate 6000000 $DIR/$path")
    }

    /** Ends a recording from [startRecording] and waits until the file is complete. */
    fun stopRecording(recording: ParcelFileDescriptor) {
        shell("pkill -INT screenrecord")
        ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() }
    }

    fun shell(command: String) {
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
}
