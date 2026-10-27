package dev.pcoffline.meadow.ui.components

import android.content.Context
import android.content.Intent
import dev.pcoffline.meadow.R

/** Opens Android's share sheet with [text]: where it goes is always the person's own choice. */
fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { startActivity(Intent.createChooser(send, getString(R.string.share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
