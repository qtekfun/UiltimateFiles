package com.qtekfun.fexplo.core.model

enum class TransferStatus { PENDING, RUNNING, WAITING_CONFLICT, COMPLETED, FAILED, CANCELLED }

/** Progress snapshot of a batch copy/move, rendered by the progress UI and the service notification. */
data class TransferProgress(
    val currentName: String,
    val processedBytes: Long,
    val totalBytes: Long,
    val processedFiles: Int,
    val totalFiles: Int,
    val bytesPerSecond: Long = 0L,
    val status: TransferStatus = TransferStatus.RUNNING,
) {
    /** 0f..1f, or null when the total size is not known. */
    val fraction: Float?
        get() = if (totalBytes > 0) (processedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** What to do when the destination already contains an entry with the same name. */
enum class ConflictResolution { OVERWRITE, SKIP, RENAME }

/** A user's answer to a name collision; [applyToAll] reuses it for the rest of the batch. */
data class ConflictDecision(val resolution: ConflictResolution, val applyToAll: Boolean = false)
