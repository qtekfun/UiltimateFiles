package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.ArchiveEngine
import com.qtekfun.ultimatefiles.domain.usecase.ArchiveFormatNotSupported
import com.qtekfun.ultimatefiles.domain.usecase.ArchiveFormat
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only view of archives as folders (`archive://…`). The archive may live on any backend: [source] is the router
 * that reads it, and anything that is not a local file is copied to [cacheDir] first because ZIP and 7z need random
 * access. Opening a file inside streams it out of the archive, so copying from here to anywhere is an ordinary copy.
 */
class ArchiveFileSystemRepository(
    private val source: () -> FileSystemRepository,
    private val cacheDir: File,
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    private class Node(val raw: String, val path: String, val name: String, val isDirectory: Boolean, val size: Long, val modified: Long)

    private class Index(val file: File, val format: ArchiveFormat, val nodes: Map<String, Node>) {
        val children: Map<String, List<Node>> = nodes.values.groupBy { it.path.substringBeforeLast('/', "") }
    }

    private val indexes = ConcurrentHashMap<String, Index>()

    override suspend fun volumes(): List<StorageVolume> = emptyList()

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val (archive, inner) = ArchivePaths.split(uriOrPath)
        val index = index(archive)
        if (inner.isNotEmpty() && index.nodes[inner]?.isDirectory != true) throw IOException("Not a folder: $inner")
        index.children[inner].orEmpty().map { it.toItem(archive) }
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val (archive, inner) = ArchivePaths.split(uriOrPath)
        if (inner.isEmpty()) {
            val name = source().stat(archive).getOrThrow().name
            return@ioResult FileItem(ArchivePaths.rootOf(archive), name, true, 0L, 0L, null, isWritable = false)
        }
        (index(archive).nodes[inner] ?: throw IOException("Not found in the archive: $inner")).toItem(archive)
    }

    override suspend fun parentOf(uriOrPath: String): String? {
        val (archive, inner) = ArchivePaths.split(uriOrPath)
        // The archive's root sits where the archive file is: going up leaves it.
        if (inner.isEmpty()) return source().parentOf(archive)
        return ArchivePaths.pathOf(archive, inner.substringBeforeLast('/', ""))
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        val (archive, inner) = ArchivePaths.split(item.path)
        val index = index(archive)
        val node = index.nodes[inner]?.takeUnless { it.isDirectory } ?: throw IOException("Not a file in the archive: $inner")
        open(index, node)
    }

    private fun readOnly(): Nothing = throw UnsupportedOperationException("Archives are read-only")

    override suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem> = ioResult { readOnly() }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> = ioResult { readOnly() }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult { readOnly() }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult { readOnly() }

    override suspend fun openOutput(parentUriOrPath: String, name: String, mimeType: String, overwrite: Boolean): Result<OutputStream> =
        ioResult { readOnly() }

    // --- reading --------------------------------------------------------------------------------

    private fun Node.toItem(archive: String) = FileItem(
        path = ArchivePaths.pathOf(archive, path),
        name = name,
        isDirectory = isDirectory,
        sizeBytes = if (isDirectory) 0L else size,
        lastModifiedMillis = modified,
        mimeType = if (isDirectory) null else mimeOf(name),
        isWritable = false,
        isHidden = name.startsWith("."),
    )

    private suspend fun index(archive: String): Index {
        val item = source().stat(archive).getOrThrow()
        val format = ArchiveFormat.of(item.name) ?: throw ArchiveFormatNotSupported(item.name)
        val file = localCopy(archive, item)
        return indexes.getOrPut("${file.path}|${file.length()}|${file.lastModified()}") { buildIndex(file, format) }
    }

    /** A real file with the archive's bytes: the file itself when it is local, else a cached copy (reused while unchanged). */
    private suspend fun localCopy(archive: String, item: FileItem): File {
        if ("://" !in archive) File(archive).takeIf { it.isFile }?.let { return it }
        cacheDir.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest(archive.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        val cached = File(cacheDir, "$key-${item.sizeBytes}-${item.lastModifiedMillis}.bin")
        if (cached.isFile && cached.length() == item.sizeBytes) return cached
        val partial = File(cacheDir, cached.name + ".part")
        source().openInput(item).getOrThrow().use { input -> partial.outputStream().use { input.copyTo(it) } }
        if (!partial.renameTo(cached)) throw IOException("Cannot cache the archive")
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MILLIS
        cacheDir.listFiles()?.filter { it != cached && it.lastModified() < cutoff }?.forEach { it.delete() }
        return cached
    }

    private fun buildIndex(file: File, format: ArchiveFormat): Index {
        val raw = ArrayList<Node>()
        fun add(name: String, directory: Boolean, size: Long, modified: Long) {
            val parts = try {
                ArchiveEngine.safeSegments(name)
            } catch (e: IOException) {
                return // an entry that would escape the folder is not shown at all
            }
            if (parts.isEmpty()) return
            raw += Node(name, parts.joinToString("/"), parts.last(), directory, size, modified)
        }
        when (format) {
            ArchiveFormat.ZIP -> ZipFile.builder().setFile(file).get().use { zip ->
                zip.entries.asSequence().forEach { add(it.name, it.isDirectory, it.size, it.time) }
            }
            ArchiveFormat.SEVEN_Z -> SevenZFile.builder().setFile(file).get().use { seven ->
                seven.entries.forEach { add(it.name, it.isDirectory, it.size, if (it.hasLastModifiedDate) it.lastModifiedDate.time else 0L) }
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> tarStream(file, format).use { tar ->
                while (true) {
                    val entry: TarArchiveEntry = tar.nextEntry ?: break
                    if (entry.isSymbolicLink || entry.isLink) continue
                    add(entry.name, entry.isDirectory, entry.size, entry.modTime?.time ?: 0L)
                }
            }
        }
        val nodes = LinkedHashMap<String, Node>()
        raw.forEach { nodes[it.path] = it }
        // Folders are often not listed as entries of their own: make the ones the files imply.
        raw.forEach { node ->
            var parent = node.path.substringBeforeLast('/', "")
            while (parent.isNotEmpty() && parent !in nodes) {
                nodes[parent] = Node(parent, parent, parent.substringAfterLast('/'), true, 0L, 0L)
                parent = parent.substringBeforeLast('/', "")
            }
        }
        return Index(file, format, nodes)
    }

    private fun tarStream(file: File, format: ArchiveFormat): TarArchiveInputStream {
        val raw = file.inputStream().buffered()
        return TarArchiveInputStream(if (format == ArchiveFormat.TAR_GZ) GzipCompressorInputStream(raw) else raw)
    }

    private fun open(index: Index, node: Node): InputStream = when (index.format) {
        ArchiveFormat.ZIP -> {
            val zip = ZipFile.builder().setFile(index.file).get()
            try {
                val entry = zip.getEntry(node.raw) ?: throw IOException("Entry vanished: ${node.raw}")
                Closing(zip.getInputStream(entry), zip)
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }
        ArchiveFormat.SEVEN_Z -> {
            val seven = SevenZFile.builder().setFile(index.file).get()
            try {
                val entry = seven.entries.firstOrNull { it.name == node.raw } ?: throw IOException("Entry vanished: ${node.raw}")
                Closing(seven.getInputStream(entry), seven)
            } catch (e: Exception) {
                seven.close()
                throw e
            }
        }
        ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> {
            val tar = tarStream(index.file, index.format)
            try {
                while (true) {
                    val entry: TarArchiveEntry = tar.nextEntry ?: throw IOException("Entry vanished: ${node.raw}")
                    if (entry.name == node.raw && !entry.isDirectory) break
                }
                // The tar stream itself ends at the end of the current entry.
                Closing(tar, tar)
            } catch (e: Exception) {
                tar.close()
                throw e
            }
        }
    }

    /** An entry's stream that also closes the archive it was read from. */
    private class Closing(input: InputStream, private val owner: Closeable) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                owner.close()
            }
        }
    }

    private companion object {
        const val CACHE_MAX_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
