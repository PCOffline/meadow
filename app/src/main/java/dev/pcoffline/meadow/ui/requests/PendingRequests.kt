@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.pcoffline.meadow.ui.requests

import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * My unanswered question about [packageName], of any kind, or null. The backend takes one per
 * app at a time, so screens that ask say so up front instead of failing after sending.
 */
fun pendingRequestFor(packageName: String): Flow<LimitRequest?> =
    AppGraph.sync.profile.flatMapLatest { state ->
        val uid = (state as? ProfileState.Ready)?.profile?.uid ?: return@flatMapLatest flowOf(null)
        AppGraph.requests.outgoing(uid, fresh = true).map { requests -> requests.firstOrNull { it.waitsAbout(packageName) } }
    }

/** Still waiting for an answer about [packageName]; past its time it has expired, settled or not. */
internal fun LimitRequest.waitsAbout(packageName: String, now: Long = System.currentTimeMillis()): Boolean =
    this.packageName == packageName && RequestStatus.effective(status, createdAtMillis, now) == RequestStatus.PENDING
