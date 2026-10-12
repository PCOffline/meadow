package dev.pcoffline.meadow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Emits immediately, then every [periodMs], for screens that show live-updating time. */
fun ticker(periodMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

/** Shares a flow while the screen is visible, keeping it warm for 5s across rotations. */
fun <T> Flow<T>.stateWhileVisible(viewModel: ViewModel, initial: T): StateFlow<T> =
    stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
