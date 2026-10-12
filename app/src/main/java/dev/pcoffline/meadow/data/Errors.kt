package dev.pcoffline.meadow.data

import androidx.annotation.StringRes
import kotlin.coroutines.cancellation.CancellationException

/** An error raised by the app itself, carrying a message that is safe to show as-is. */
class FriendlyException(@StringRes val messageRes: Int, vararg val formatArgs: Any) : Exception()

/** Like [runCatching], but never swallows coroutine cancellation. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
