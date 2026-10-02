package com.qtekfun.fexplo.data.repository

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.StorageVolume
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import java.io.InputStream
import java.io.OutputStream

/** Single entry point for the app: `content://` locations go to SAF, everything else to the local backend. */
class RoutingFileSystemRepository(
    private val local: FileSystemRepository,
    private val saf: FileSystemRepository,
) : FileSystemRepository {

    private fun backendFor(path: String) = if (path.startsWith(SAF_SCHEME)) saf else local

    override suspend fun volumes(): List<StorageVolume> = local.volumes() + saf.volumes()

    override suspend fun listFiles(uriOrPath: String) = backendFor(uriOrPath).listFiles(uriOrPath)

    override suspend fun stat(uriOrPath: String) = backendFor(uriOrPath).stat(uriOrPath)

    override suspend fun parentOf(uriOrPath: String) = backendFor(uriOrPath).parentOf(uriOrPath)

    override suspend fun createDirectory(parentUriOrPath: String, name: String) =
        backendFor(parentUriOrPath).createDirectory(parentUriOrPath, name)

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String) =
        backendFor(parentUriOrPath).createFile(parentUriOrPath, name, mimeType)

    override suspend fun delete(items: List<FileItem>): Result<Unit> {
        for ((isSaf, group) in items.groupBy { it.path.startsWith(SAF_SCHEME) }) {
            val result = (if (isSaf) saf else local).delete(group)
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    override suspend fun rename(item: FileItem, newName: String) = backendFor(item.path).rename(item, newName)

    override suspend fun openInput(item: FileItem): Result<InputStream> = backendFor(item.path).openInput(item)

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = backendFor(parentUriOrPath).openOutput(parentUriOrPath, name, mimeType, overwrite)

    private companion object {
        const val SAF_SCHEME = "content://"
    }
}
