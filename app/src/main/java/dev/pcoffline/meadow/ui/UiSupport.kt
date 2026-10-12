package dev.pcoffline.meadow.ui

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.data.FriendlyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Plays a shower of petals (and a little haptic tap) for happy moments. */
val LocalCelebrate = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Runs one user action at a time and exposes whether it's busy and what went wrong.
 *
 * Actions run in the app's process scope, so a rotation or leaving the screen never cancels a
 * write halfway. Callbacks only run while the screen is still showing.
 */
class ActionState(private val context: Context, private val snackbar: SnackbarHostState) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    internal var attached = true

    fun run(successMessage: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        AppGraph.scope.launch(Dispatchers.Main.immediate) {
            busy = true
            error = null
            try {
                block()
                if (attached) {
                    onSuccess()
                    successMessage?.let { launch { snackbar.showSnackbar(it) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Meadow", "Action failed", e)
                error = e.friendlyMessage(context)
            } finally {
                busy = false
            }
        }
    }

    fun toast(message: String) {
        AppGraph.scope.launch(Dispatchers.Main.immediate) { snackbar.showSnackbar(message) }
    }
}

@Composable
fun rememberAction(): ActionState {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val state = remember { ActionState(context.applicationContext, snackbar) }
    DisposableEffect(state) {
        state.attached = true
        onDispose { state.attached = false }
    }
    return state
}

/** Turns any failure into something kind and human to show. */
fun Throwable.friendlyMessage(context: Context): String = when (this) {
    is FriendlyException -> context.getString(messageRes, *formatArgs)
    // Cloud Function errors already carry friendly text written in the backend's copy.ts.
    is Backend.CallFailed -> when {
        unreachable -> context.getString(R.string.error_offline)
        !message.isNullOrBlank() -> message.orEmpty()
        else -> context.getString(R.string.error_generic)
    }
    is FirebaseNetworkException -> context.getString(R.string.error_offline)
    is FirebaseAuthException -> context.getString(
        when (errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.error_auth_invalid_email
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> R.string.error_auth_wrong_credentials
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> R.string.error_auth_email_taken
            "ERROR_WEAK_PASSWORD" -> R.string.error_auth_weak_password
            "ERROR_TOO_MANY_REQUESTS" -> R.string.error_auth_too_many
            "ERROR_REQUIRES_RECENT_LOGIN" -> R.string.error_auth_recent_login
            else -> R.string.error_generic
        },
    )
    else -> context.getString(R.string.error_generic)
}

fun relativeTime(context: Context, millis: Long): String {
    val now = System.currentTimeMillis()
    if (millis <= 0L || now - millis < 60_000L) return context.getString(R.string.time_just_now)
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

@Composable
fun relativeTime(millis: Long): String = relativeTime(LocalContext.current, millis)
