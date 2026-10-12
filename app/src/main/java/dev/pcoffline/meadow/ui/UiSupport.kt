package dev.pcoffline.meadow.ui

import android.text.format.DateUtils
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Plays a shower of petals (and a little haptic tap) for happy moments. */
val LocalCelebrate = staticCompositionLocalOf<() -> Unit> { {} }

/** Runs one user action at a time, exposing whether it is busy and what went wrong. */
class ActionState(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun run(successMessage: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                block()
                onSuccess()
                successMessage?.let { launch { snackbar.showSnackbar(it) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
}

@Composable
fun rememberAction(): ActionState {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    return remember { ActionState(scope, snackbar) }
}

fun relativeTime(millis: Long): String {
    if (millis <= 0L) return "just now"
    val now = System.currentTimeMillis()
    if (now - millis < 60_000L) return "just now"
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}
