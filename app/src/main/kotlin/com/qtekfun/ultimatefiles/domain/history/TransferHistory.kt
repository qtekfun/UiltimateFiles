package com.qtekfun.ultimatefiles.domain.history

import com.qtekfun.ultimatefiles.core.model.ConnectionProblemCodec
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.flow.Flow

enum class HistoryOperation { COPY, MOVE, DELETE, COMPRESS, EXTRACT }

/** One finished (or failed / cancelled) copy, move or delete. */
data class HistoryEntry(
    val finishedAtMillis: Long,
    val operation: HistoryOperation,
    val status: TransferStatus,
    val itemCount: Int,
    val totalBytes: Long,
    /** Name of the first item; the UI shows "name" or "N items" depending on [itemCount]. */
    val firstItemName: String,
    val targetName: String?,
    val error: String?,
)

/** Persistent, newest-first log of finished operations. */
interface TransferHistoryRepository {
    val entries: Flow<List<HistoryEntry>>

    suspend fun add(entry: HistoryEntry)

    suspend fun clear()
}

/** Turns a finished operation into a [HistoryEntry]; history problems never affect the operation itself. */
class TransferHistoryRecorder(
    private val history: TransferHistoryRepository,
    private val files: FileSystemRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    /** The name of a server account, for the entries that failed on a connection problem. */
    private val accountLabelOf: suspend (accountId: String) -> String? = { null },
) {
    /** Whether [item] is still there; used to drop already-moved items when an interrupted batch is offered again. */
    suspend fun exists(item: FileItem): Boolean = files.stat(item.path).isSuccess

    /** Display name of the destination folder, or null when it cannot be read. */
    suspend fun targetNameOf(request: TransferRequest): String? = files.stat(request.targetDirectory).getOrNull()?.name

    suspend fun recordTransfer(request: TransferRequest, finalProgress: TransferProgress?) {
        val targetName = targetNameOf(request)
        val status = finalProgress?.status ?: TransferStatus.CANCELLED
        record(
            HistoryEntry(
                finishedAtMillis = clockMillis(),
                operation = when (request.operation) {
                    OperationType.CUT -> HistoryOperation.MOVE
                    OperationType.COMPRESS -> HistoryOperation.COMPRESS
                    OperationType.EXTRACT -> HistoryOperation.EXTRACT
                    else -> HistoryOperation.COPY
                },
                status = status,
                itemCount = request.items.size,
                totalBytes = finalProgress?.processedBytes ?: 0L,
                firstItemName = request.items.firstOrNull()?.name.orEmpty(),
                targetName = targetName,
                error = errorOf(finalProgress),
            ),
        )
    }

    /** A connection problem is stored as a code and the account's name (see [ConnectionProblemCodec]), anything else as its message. */
    private suspend fun errorOf(progress: TransferProgress?): String? {
        val problem = progress?.connectionProblem ?: return progress?.error
        val label = problem.accountId?.let { accountLabelOf(it) }
        return ConnectionProblemCodec.encode(problem.kind, label)
    }

    suspend fun recordDelete(items: List<FileItem>, result: Result<Unit>) {
        record(
            HistoryEntry(
                finishedAtMillis = clockMillis(),
                operation = HistoryOperation.DELETE,
                status = if (result.isSuccess) TransferStatus.COMPLETED else TransferStatus.FAILED,
                itemCount = items.size,
                totalBytes = items.sumOf { it.sizeBytes },
                firstItemName = items.firstOrNull()?.name.orEmpty(),
                targetName = null,
                error = result.exceptionOrNull()?.message,
            ),
        )
    }

    private suspend fun record(entry: HistoryEntry) {
        try {
            history.add(entry)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The log is best effort.
        }
    }
}
