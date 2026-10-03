package com.qtekfun.fexplo.domain.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Lets a running transfer be paused between chunks: the copier calls [awaitResumed] before every read, so a
 * pause takes effect within one buffer (at most 1 MiB) and the streams stay open until [resume].
 */
class PauseGate {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun pause() {
        _paused.value = true
    }

    fun resume() {
        _paused.value = false
    }

    /** Returns at once when not paused; otherwise suspends (cancellably) until [resume]. */
    suspend fun awaitResumed() {
        if (_paused.value) _paused.first { !it }
    }
}
