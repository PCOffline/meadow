package dev.pcoffline.meadow.ui

import android.content.Context
import android.os.SystemClock
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.data.FriendlyException
import dev.pcoffline.meadow.data.Trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

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

    private var finishedAt = -SETTLE_MS

    /**
     * Runs [block] unless an action is already running or only just succeeded, so a double tap
     * acts once. A failed action can be retried straight away.
     */
    fun run(successMessage: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy || SystemClock.elapsedRealtime() - finishedAt < SETTLE_MS) return
        busy = true
        AppGraph.scope.launch(Dispatchers.Main.immediate) {
            error = null
            try {
                block()
                finishedAt = SystemClock.elapsedRealtime()
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

    private companion object {
        const val SETTLE_MS = 600L
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

/**
 * Turns any failure into something kind and human to show. People only learn what they can act
 * on (offline, sign in again, wait a bit); a fault on the server's side is just "try again later".
 * Trace builds add the technical cause underneath, for whoever is debugging.
 */
fun Throwable.friendlyMessage(context: Context): String {
    val message = plainMessage(context)
    return if (Trace.enabled) "$message\n\n${technicalCause()}" else message
}

private fun Throwable.technicalCause(): String = when (this) {
    is Backend.CallFailed -> "Cloud Function failed: ${code ?: "unreachable"} ${cause?.message.orEmpty()}"
    is FirebaseFirestoreException -> "Firestore: $code $message"
    else -> "${javaClass.simpleName}: $message"
}.trim()

private fun Throwable.plainMessage(context: Context): String = when (this) {
    is FriendlyException -> context.getString(messageRes, *formatArgs)
    // Cloud Function errors already carry friendly text written in the backend's copy.ts.
    is Backend.CallFailed -> when {
        unreachable -> context.getString(R.string.error_offline)
        !message.isNullOrBlank() -> message.orEmpty()
        else -> context.getString(
            when (code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> R.string.error_auth_recent_login
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> R.string.error_server_busy
                // A missing function, refused call or crash: nothing the person can do but wait.
                else -> R.string.error_unavailable
            },
        )
    }
    is FirebaseNetworkException -> context.getString(R.string.error_offline)
    is FirebaseFirestoreException -> context.getString(
        when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.error_offline
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> R.string.error_auth_recent_login
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> R.string.error_server_busy
            // Refused by the rules, no database, or a fault on the server.
            else -> R.string.error_unavailable
        },
    )
    is FirebaseAuthException -> context.getString(
        when (errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.error_auth_invalid_email
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> R.string.error_auth_wrong_credentials
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> R.string.error_auth_email_taken
            "ERROR_WEAK_PASSWORD" -> R.string.error_auth_weak_password
            "ERROR_TOO_MANY_REQUESTS" -> R.string.error_auth_too_many
            "ERROR_REQUIRES_RECENT_LOGIN" -> R.string.error_auth_recent_login
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> R.string.error_auth_other_method
            "ERROR_INVALID_ACTION_CODE", "ERROR_EXPIRED_ACTION_CODE" -> R.string.error_link_invalid
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

/** The app's language, read so that composables update when it changes. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
