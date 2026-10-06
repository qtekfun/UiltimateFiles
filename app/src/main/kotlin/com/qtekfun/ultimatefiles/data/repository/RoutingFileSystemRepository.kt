package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.data.system.RemovableStorage
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import java.io.InputStream
import java.io.OutputStream

/** Single entry point for the app: `content://` goes to SAF, `dav://` to WebDAV, `sftp://` to SFTP, `archive://` to the archive reader, everything else to the local backend. */
class RoutingFileSystemRepository(
    private val local: FileSystemRepository,
    private val saf: FileSystemRepository,
    private val webDav: FileSystemRepository,
    private val sftp: FileSystemRepository,
    private val archive: FileSystemRepository,
    private val smb: FileSystemRepository = archive,
) : FileSystemRepository {

    private fun backendFor(path: String) = when {
        path.startsWith(SAF_SCHEME) -> saf
        path.startsWith(WebDavFileSystemRepository.SCHEME) -> webDav
        path.startsWith(SftpFileSystemRepository.SCHEME) -> sftp
        path.startsWith(SmbFileSystemRepository.SCHEME) -> smb
        ArchivePaths.isArchivePath(path) -> archive
        else -> local
    }

    override suspend fun volumes(): List<StorageVolume> {
        val byPath = local.volumes()
        // A drive shown by path is not listed again through a folder that was granted for it before.
        val granted = saf.volumes().filterNot { tree ->
            byPath.any { it.id.startsWith(RemovableStorage.ID_PREFIX) && RemovableStorage.isSameVolume(tree.id, it.id) }
        }
        return byPath + granted + webDav.volumes() + sftp.volumes() + smb.volumes() + archive.volumes()
    }

    override suspend fun listFiles(uriOrPath: String) = backendFor(uriOrPath).listFiles(uriOrPath)

    override suspend fun stat(uriOrPath: String) = backendFor(uriOrPath).stat(uriOrPath)

    override suspend fun parentOf(uriOrPath: String) = backendFor(uriOrPath).parentOf(uriOrPath)

    override suspend fun createDirectory(parentUriOrPath: String, name: String) =
        backendFor(parentUriOrPath).createDirectory(parentUriOrPath, name)

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String) =
        backendFor(parentUriOrPath).createFile(parentUriOrPath, name, mimeType)

    override suspend fun delete(items: List<FileItem>): Result<Unit> {
        for ((backend, group) in items.groupBy { backendFor(it.path) }) {
            val result = backend.delete(group)
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
