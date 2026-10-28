package dev.pcoffline.meadow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import dev.pcoffline.meadow.test.R

/**
 * A stand-in browser for the end-to-end tests: its address bar shows the address it was opened
 * with, under the same id as Chrome's. Debug builds of Meadow read it like a real browser's
 * (BuildConfig.TEST_BROWSER); release builds never do.
 */
class StandInBrowserActivity : Activity() {
    private lateinit var bar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bar = TextView(this).apply { id = R.id.url_bar }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar)
                addView(TextView(this@StandInBrowserActivity).apply { text = "A page" })
            },
        )
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        bar.text = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
