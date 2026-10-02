package com.qtekfun.fexplo.domain.repository

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationProgress
import com.qtekfun.fexplo.core.model.StorageVolume
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over any file backend (local paths, SAF, future WebDAV…).
 *
 * Implementations must perform all I/O on `Dispatchers.IO`; callers may invoke
 * these from any dispatcher, including Main.
 */
interface FileSystemRepository {
    /** Storage roots currently available (internal, USB OTG…). */
    suspend fun volumes(): List<StorageVolume>

    /** Direct children of [directoryId]. Throws [java.io.IOException] if unreadable. */
    suspend fun list(directoryId: String): List<FileItem>

    /** Metadata of one entry, or null if it no longer exists. */
    suspend fun stat(id: String): FileItem?

    /** Parent directory id, or null when [id] is a volume root. */
    suspend fun parentOf(id: String): String?

    suspend fun createDirectory(parentId: String, name: String): FileItem

    /** Copies [sourceIds] into [targetDirectoryId], emitting progress. Cancellable. */
    fun copy(sourceIds: List<String>, targetDirectoryId: String): Flow<OperationProgress>

    /** Moves [sourceIds] into [targetDirectoryId], emitting progress. Cancellable. */
    fun move(sourceIds: List<String>, targetDirectoryId: String): Flow<OperationProgress>

    fun delete(ids: List<String>): Flow<OperationProgress>
}
