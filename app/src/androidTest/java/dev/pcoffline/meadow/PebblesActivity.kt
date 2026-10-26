package dev.pcoffline.meadow

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Pebbles, an everyday app for the tests to limit and open. It ships in the test APK, so it's a
 * separate app from Meadow, which can't limit itself.
 */
class PebblesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Pebbles" })
    }
}
