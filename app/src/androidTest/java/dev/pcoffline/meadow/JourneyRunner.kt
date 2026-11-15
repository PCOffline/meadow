package dev.pcoffline.meadow

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs the phone tests with a new connection for each request.
 *
 * Android keeps a connection open for the next request. The Firebase emulators close a quiet
 * connection after a few seconds. A sign-in that went out on a connection that the emulator had
 * just closed failed with "unexpected end of stream", and its journey with it. A person on a real
 * phone taps again; a journey doesn't.
 *
 * Android reads the setting when it opens its first connection, so it is set here, before the
 * app starts.
 */
class JourneyRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        System.setProperty("http.keepAlive", "false")
        super.onCreate(arguments)
    }
}
