package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Path-based backend for the device's shared storage (needs all-files access on Android 11+). */
class LocalFileSystemRepository(
    private val root: File,
    private val label: String,
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    override suspend fun volumes(): List<StorageVolume> = listOf(
        StorageVolume(
            id = INTERNAL_ID,
            label = label,
            rootPath = root.path,
            kind = StorageKind.INTERNAL,
            isEjectable = false,
        ),
    )

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val children = File(uriOrPath).listFiles() ?: throw IOException("Cannot list $uriOrPath")
        children.map(::toItem)
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val file = File(uriOrPath)
        if (!file.exists()) throw IOException("Not found: $uriOrPath")
        toItem(file).copy(permissions = posixPermissions(file))
    }

    override suspend fun parentOf(uriOrPath: String): String? {
        if (uriOrPath == root.path) return null
        return File(uriOrPath).parent
    }

    override suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val dir = File(parentUriOrPath, name)
        if (dir.exists()) throw IOException("Already exists: $name")
        if (!dir.mkdir()) throw IOException("Cannot create directory $name")
        toItem(dir)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> =
        ioResult {
            requireValidName(name)
            val file = File(parentUriOrPath, name)
            if (!file.createNewFile()) throw IOException("Already exists: $name")
            toItem(file)
        }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            if (!File(item.path).deleteRecursively()) throw IOException("Cannot delete ${item.name}")
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val source = File(item.path)
        val target = File(source.parentFile, newName)
        if (target.exists()) throw IOException("Already exists: $newName")
        if (!source.renameTo(target)) throw IOException("Cannot rename ${item.name}")
        toItem(target)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        File(item.path).inputStream()
    }

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = ioResult {
        requireValidName(name)
        val file = File(parentUriOrPath, name)
        if (file.isDirectory) throw IOException("$name is a directory")
        if (file.exists() && !overwrite) throw IOException("Already exists: $name")
        file.outputStream()
    }

    /** Null where the file system has no POSIX attributes (some shared-storage mounts). */
    private fun posixPermissions(file: File): String? = try {
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))
    } catch (e: Exception) {
        null
    }

    private fun toItem(file: File): FileItem {
        val isDirectory = file.isDirectory
        return FileItem(
            path = file.path,
            name = file.name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else file.length(),
            lastModifiedMillis = file.lastModified(),
            mimeType = if (isDirectory) null else mimeOf(file.name),
            isWritable = file.canWrite(),
            isHidden = file.isHidden,
        )
    }

    private companion object {
        const val INTERNAL_ID = "internal"
    }
}
