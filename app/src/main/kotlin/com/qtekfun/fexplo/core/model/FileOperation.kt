package com.qtekfun.fexplo.core.model

/** An I/O operation requested by the user. Immutable so it can travel in UI events. */
sealed interface FileOperation {
    val sourceIds: List<String>

    data class Copy(override val sourceIds: List<String>, val targetDirectoryId: String) : FileOperation
    data class Move(override val sourceIds: List<String>, val targetDirectoryId: String) : FileOperation
    data class Delete(override val sourceIds: List<String>) : FileOperation
}

/** Progress snapshot emitted while a [FileOperation] runs. */
data class OperationProgress(
    val currentName: String,
    val processedBytes: Long,
    val totalBytes: Long,
    val processedFiles: Int,
    val totalFiles: Int,
    val isDone: Boolean = false,
) {
    /** 0f..1f, or null when the total size is not known. */
    val fraction: Float?
        get() = if (totalBytes > 0) (processedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}
