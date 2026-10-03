package com.qtekfun.ultimatefiles.domain.transfer

import com.qtekfun.ultimatefiles.core.model.ConflictDecision
import com.qtekfun.ultimatefiles.core.model.ConflictPrompt
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferSummary
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRecorder
import com.qtekfun.ultimatefiles.domain.usecase.ConflictResolver
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
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
    /** The batch being processed, if any. */
    val active: TransferSummary? = null,
    /** Batches waiting behind [active]. */
    val queuedTasks: List<TransferSummary> = emptyList(),
    val paused: Boolean = false,
) {
    val queued: Int get() = queuedTasks.size
}

/**
 * Queue and shared state for batch transfers. UI code calls [enqueue]; the foreground service
 * calls [claimWorker]/[next]/[run] so the work survives the app going to the background.
 */
class TransferCoordinator(
    private val engine: TransferEngine,
    private val recorder: TransferHistoryRecorder,
    private val gate: PauseGate = engine.pauseGate,
    private val journal: TransferJournal = NoJournal,
    private val launcher: TransferServiceLauncher,
) : ConflictResolver {
    private val lock = Any()
    private val queue = ArrayDeque<TransferRequest>()
    private var workerRunning = false

    /** Every batch that has not finished (the running one and the queued ones), mirrored in the [journal]. */
    private val unfinished = ArrayList<TransferRequest>()
    private var interruptedRequests: List<TransferRequest> = emptyList()

    private val _interrupted = MutableStateFlow<List<TransferSummary>>(emptyList())

    /** Batches a previous run left unfinished because the app was stopped; the user decides whether to resume them. */
    val interrupted: StateFlow<List<TransferSummary>> = _interrupted.asStateFlow()

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    @Volatile private var activeJob: Job? = null
    @Volatile private var pendingConflict: CompletableDeferred<ConflictDecision>? = null

    /** Reads the journal; call it off the main thread once at start-up. Does nothing while transfers are in progress. */
    fun loadInterrupted() {
        val loaded = synchronized(lock) {
            if (unfinished.isNotEmpty()) return
            journal.load().also { interruptedRequests = it }
        }
        _interrupted.value = loaded.map(::summaryOf)
    }

    /** Queues what was interrupted again, skipping items that are gone (a move may have finished them). */
    suspend fun restoreInterrupted() {
        val requests = synchronized(lock) {
            if (interruptedRequests.isEmpty()) interruptedRequests = journal.load()
            interruptedRequests.also { interruptedRequests = emptyList() }
        }
        _interrupted.value = emptyList()
        // Whatever is not queued again is gone from the journal too.
        synchronized(lock) { journal.save(unfinished.toList()) }
        for (request in requests) {
            val present = request.items.filter { recorder.exists(it) }
            if (present.isNotEmpty()) enqueue(request.copy(items = present))
        }
    }

    /** The user does not want the interrupted batches back. */
    fun discardInterrupted() {
        synchronized(lock) {
            interruptedRequests = emptyList()
            journal.save(unfinished.toList())
        }
        _interrupted.value = emptyList()
    }

    fun enqueue(request: TransferRequest) {
        val queued = synchronized(lock) {
            queue.addLast(request)
            unfinished.add(request)
            journal.save(unfinished.toList())
            queue.map(::summaryOf)
        }
        _state.update { it.copy(queuedTasks = queued) }
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
        val (request, remaining) = synchronized(lock) {
            val head = queue.removeFirstOrNull()
            if (head == null) workerRunning = false
            head to queue.map(::summaryOf)
        }
        if (request == null) gate.resume() // the next batch must not start paused
        _state.update { it.copy(queuedTasks = remaining, paused = if (request == null) false else it.paused) }
        return request
    }

    /** Runs one request to a terminal state; returns that final progress snapshot. */
    suspend fun run(request: TransferRequest): TransferProgress? {
        _state.update { it.copy(progress = null, conflict = null, active = summaryOf(request)) }
        recorder.targetNameOf(request)?.let { name ->
            _state.update { s -> s.copy(active = s.active?.copy(targetName = name)) }
        }
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
        _state.update { it.copy(active = null) }
        // Finished, failed or cancelled: either way it is no longer "interrupted".
        synchronized(lock) {
            unfinished.remove(request)
            journal.save(unfinished.toList())
        }
        withContext(NonCancellable) { recorder.recordTransfer(request, finalProgress) }
        return finalProgress
    }

    /** Cancels the running transfer and drops everything still queued. */
    fun cancelAll() {
        synchronized(lock) {
            queue.clear()
            unfinished.clear()
            journal.save(emptyList())
        }
        gate.resume()
        _state.update { it.copy(queuedTasks = emptyList(), paused = false) }
        activeJob?.cancel()
    }

    /** Suspends the running copy between chunks; queued batches wait behind it. */
    fun pause() {
        gate.pause()
        _state.update { it.copy(paused = true) }
    }

    fun resume() {
        gate.resume()
        _state.update { it.copy(paused = false) }
    }

    fun togglePause() = if (_state.value.paused) resume() else pause()

    private fun summaryOf(request: TransferRequest) = TransferSummary(
        operation = request.operation,
        itemCount = request.items.size,
        firstItemName = request.items.firstOrNull()?.name.orEmpty(),
    )

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
