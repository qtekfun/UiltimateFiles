package com.qtekfun.ultimatefiles.core.model

/** [VERIFYING] is the optional read-back check that follows each copied file. */
enum class TransferStatus { PENDING, RUNNING, VERIFYING, WAITING_CONFLICT, COMPLETED, FAILED, CANCELLED }

/** Progress snapshot of a batch copy/move, rendered by the progress UI and the service notification. */
data class TransferProgress(
    val currentName: String,
    val processedBytes: Long,
    val totalBytes: Long,
    val processedFiles: Int,
    val totalFiles: Int,
    val bytesPerSecond: Long = 0L,
    val status: TransferStatus = TransferStatus.RUNNING,
    val error: String? = null,
) {
    /** 0f..1f, or null when the total size is not known. */
    val fraction: Float?
        get() = if (totalBytes > 0) (processedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    /** Estimated seconds left at the current speed, or null while the speed is unknown. */
    val remainingSeconds: Long?
        get() = if (bytesPerSecond > 0 && totalBytes > processedBytes) (totalBytes - processedBytes) / bytesPerSecond else null
}

/** What to do when the destination already contains an entry with the same name. */
enum class ConflictResolution { OVERWRITE, SKIP, RENAME }

/** A user's answer to a name collision; [applyToAll] reuses it for the rest of the batch. */
data class ConflictDecision(val resolution: ConflictResolution, val applyToAll: Boolean = false)

/**
 * A batch to copy or move ([OperationType.COPY] / [OperationType.CUT]) into [targetDirectory], to pack [items] into one
 * ZIP there ([OperationType.COMPRESS]) or to unpack each archive of [items] into its own folder ([OperationType.EXTRACT]).
 */
data class TransferRequest(
    val operation: OperationType,
    val items: List<FileItem>,
    val targetDirectory: String,
    /** Read every copied file back and compare its SHA-256 before the source of a move is deleted. */
    val verify: Boolean = false,
    /** [OperationType.COMPRESS] only: file name of the ZIP to create inside [targetDirectory]. */
    val archiveName: String? = null,
)

/** Asks the user how to resolve a name collision between [source] and the entry already at the destination. */
data class ConflictPrompt(val source: FileItem, val existing: FileItem)

/** What a queued or running batch is about, for the history screen. [targetName] is unknown until it runs. */
data class TransferSummary(
    val operation: OperationType,
    val itemCount: Int,
    val firstItemName: String,
    val targetName: String? = null,
)
