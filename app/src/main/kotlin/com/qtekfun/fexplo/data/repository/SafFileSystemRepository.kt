package com.qtekfun.fexplo.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.StorageKind
import com.qtekfun.fexplo.core.model.StorageVolume
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Access Framework backend: every path is a document/tree URI string.
 * Used for USB OTG drives and any folder the user grants through the system picker.
 */
class SafFileSystemRepository(private val context: Context) : FileSystemRepository {
    private val resolver get() = context.contentResolver

    /** Persists the grant returned by `ACTION_OPEN_DOCUMENT_TREE` so the volume survives restarts. */
    fun addTree(treeUri: Uri) {
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    override suspend fun volumes(): List<StorageVolume> = withContext(Dispatchers.IO) {
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .mapNotNull { permission ->
                val root = DocumentFile.fromTreeUri(context, permission.uri)
                if (root == null || !root.exists()) return@mapNotNull null // e.g. drive unplugged
                val treeId = DocumentsContract.getTreeDocumentId(permission.uri)
                val isPrimary = treeId.startsWith("primary")
                StorageVolume(
                    id = permission.uri.toString(),
                    label = root.name ?: treeId,
                    rootPath = root.uri.toString(),
                    kind = if (isPrimary) StorageKind.INTERNAL else StorageKind.USB_OTG,
                    isEjectable = !isPrimary,
                )
            }
    }

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val dir = documentAt(uriOrPath)
        if (!dir.isDirectory) throw IOException("Not a directory: $uriOrPath")
        dir.listFiles().map(::toItem)
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        toItem(documentAt(uriOrPath))
    }

    /** Null for a tree root, and also when the provider cannot resolve the document path. */
    override suspend fun parentOf(uriOrPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriOrPath)
            val ids = DocumentsContract.findDocumentPath(resolver, uri)?.path
            if (ids == null || ids.size < 2) {
                null
            } else {
                DocumentsContract.buildDocumentUriUsingTree(uri, ids[ids.size - 2]).toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val created = documentAt(parentUriOrPath).createDirectory(name)
            ?: throw IOException("Cannot create directory $name")
        toItem(created)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> =
        ioResult {
            requireValidName(name)
            val created = documentAt(parentUriOrPath).createFile(mimeType, name)
                ?: throw IOException("Cannot create file $name")
            toItem(created)
        }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            val document = documentAt(item.path)
            if (document.exists() && !document.delete()) throw IOException("Cannot delete ${item.name}")
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val document = documentAt(item.path)
        if (!document.renameTo(newName)) throw IOException("Cannot rename ${item.name}")
        toItem(document)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        resolver.openInputStream(Uri.parse(item.path)) ?: throw IOException("Cannot open ${item.name}")
    }

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = ioResult {
        requireValidName(name)
        val parent = documentAt(parentUriOrPath)
        val existing = parent.findFile(name)
        val target = when {
            existing == null -> parent.createFile(mimeType, name) ?: throw IOException("Cannot create file $name")
            existing.isDirectory -> throw IOException("$name is a directory")
            !overwrite -> throw IOException("Already exists: $name")
            else -> existing
        }
        // "wt" truncates, so overwriting a longer file does not leave stale trailing bytes.
        resolver.openOutputStream(target.uri, "wt") ?: throw IOException("Cannot write $name")
    }

    private fun documentAt(path: String): DocumentFile =
        DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: throw IOException("Unsupported location: $path")

    private fun toItem(document: DocumentFile): FileItem {
        val isDirectory = document.isDirectory
        return FileItem(
            path = document.uri.toString(),
            name = document.name.orEmpty(),
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else document.length(),
            lastModifiedMillis = document.lastModified(),
            mimeType = if (isDirectory) null else document.type,
            isWritable = document.canWrite(),
            isHidden = document.name.orEmpty().startsWith('.'),
        )
    }
}
