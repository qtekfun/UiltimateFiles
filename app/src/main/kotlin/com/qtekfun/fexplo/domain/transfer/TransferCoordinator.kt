package com.qtekfun.fexplo.domain.transfer

import com.qtekfun.fexplo.core.model.ConflictDecision
import com.qtekfun.fexplo.core.model.ConflictPrompt
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.domain.history.TransferHistoryRecorder
import com.qtekfun.fexplo.domain.usecase.ConflictResolver
import com.qtekfun.fexplo.domain.usecase.TransferEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts the foreground service that will drain the coordinator's queue. */
fun interface TransferServiceLauncher {
    fun launch()
}

/** What the UI and the service notification observe. */
data class TransferState(
    val progress: TransferProgress? = null,
    val conflict: ConflictPrompt? = null,
    val queued: Int = 0,
)

/**
 * Queue and shared state for batch transfers. UI code calls [enqueue]; the foreground service
 * calls [claimWorker]/[next]/[run] so the work survives the app going to the background.
 */
class TransferCoordinator(
    private val engine: TransferEngine,
    private val recorder: TransferHistoryRecorder,
    private val launcher: TransferServiceLauncher,
) : ConflictResolver {
    private val lock = Any()
    private val queue = ArrayDeque<TransferRequest>()
    private var workerRunning = false

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    @Volatile private var activeJob: Job? = null
    @Volatile private var pendingConflict: CompletableDeferred<ConflictDecision>? = null

    fun enqueue(request: TransferRequest) {
        val queued = synchronized(lock) {
            queue.addLast(request)
            queue.size
        }
        _state.update { it.copy(queued = queued) }
        launcher.launch()
    }

    /** Called by the service on every start command; true means the caller must run a worker loop. */
    fun claimWorker(): Boolean = synchronized(lock) {
        if (workerRunning || queue.isEmpty()) {
            false
        } else {
            workerRunning = true
            true
        }
    }

    /** True when no worker is running and nothing is queued. */
    fun isIdle(): Boolean = synchronized(lock) { !workerRunning && queue.isEmpty() }

    /** Next queued request, or null (which also releases the worker slot) when the queue is drained. */
    fun next(): TransferRequest? {
        val request = synchronized(lock) {
            val head = queue.removeFirstOrNull()
            if (head == null) workerRunning = false
            head
        }
        _state.update { it.copy(queued = synchronized(lock) { queue.size }) }
        return request
    }

    /** Runs one request to a terminal state; returns that final progress snapshot. */
    suspend fun run(request: TransferRequest): TransferProgress? {
        _state.update { it.copy(progress = null, conflict = null) }
        coroutineScope {
            val job = launch {
                engine.execute(request, this@TransferCoordinator).collect { progress ->
                    _state.update { it.copy(progress = progress) }
                }
            }
            activeJob = job
            job.join()
            activeJob = null
            if (job.isCancelled) {
                _state.update { it.copy(progress = it.progress?.copy(status = TransferStatus.CANCELLED)) }
            }
        }
        val finalProgress = _state.value.progress
        withContext(NonCancellable) { recorder.recordTransfer(request, finalProgress) }
        return finalProgress
    }

    /** Cancels the running transfer and drops everything still queued. */
    fun cancelAll() {
        synchronized(lock) { queue.clear() }
        _state.update { it.copy(queued = 0) }
        activeJob?.cancel()
    }

    fun answerConflict(decision: ConflictDecision) {
        pendingConflict?.complete(decision)
    }

    override suspend fun resolve(source: FileItem, existing: FileItem): ConflictDecision {
        val answer = CompletableDeferred<ConflictDecision>()
        pendingConflict = answer
        _state.update { it.copy(conflict = ConflictPrompt(source, existing)) }
        try {
            return answer.await()
        } finally {
            pendingConflict = null
            _state.update { it.copy(conflict = null) }
        }
    }
}
