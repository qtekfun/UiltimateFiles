package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.core.util.TransferSpeedMeter
import com.qtekfun.ultimatefiles.core.util.uniqueName
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.transfer.PauseGate
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Packs files into a ZIP and unpacks ZIP / TAR / TAR.GZ archives, entirely through [FileSystemRepository] so it
 * works on every backend. Extraction is all-or-nothing per archive: it goes into a new folder that is removed
 * again when anything fails, and entries that would escape that folder (`../`) are refused.
 */
/** [repository] must be the app-wide router: 7z archives are read through its `archive://` backend. */
class ArchiveEngine(
    private val repository: FileSystemRepository,
    private val gate: PauseGate,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    fun execute(request: TransferRequest): Flow<TransferProgress> =
        channelFlow { Run(request, this).run() }
            .conflate()
            .flowOn(Dispatchers.IO)

    private inner class Run(private val request: TransferRequest, private val out: ProducerScope<TransferProgress>) {
        private val meter = TransferSpeedMeter(clockMillis)
        private var totalBytes = 0L
        private var totalFiles = 0
        private var doneBytes = 0L
        private var doneFiles = 0
        private var current = ""
        private var lastEmitMillis = 0L

        suspend fun run() {
            try {
                if (request.operation == OperationType.COMPRESS) compress() else extractAll()
                publish(TransferStatus.COMPLETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(TransferStatus.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }

        // ---- compress -------------------------------------------------------------------------------------

        private suspend fun compress() {
            val entries = ArrayList<Entry>()
            request.items.forEach { collect(it, it.name, entries) }
            entries.filterNot { it.item.isDirectory }.forEach {
                totalFiles++
                totalBytes += it.item.sizeBytes
            }
            publish(TransferStatus.RUNNING)

            val taken = repository.listFiles(request.targetDirectory).getOrThrow().map { it.name.lowercase() }.toSet()
            val wanted = (request.archiveName ?: defaultArchiveName()).let { if (it.endsWith(".zip", true)) it else "$it.zip" }
            val name = uniqueName(wanted) { it.lowercase() in taken }
            val output = repository.openOutput(request.targetDirectory, name, "application/zip", overwrite = false).getOrThrow()
            try {
                ZipArchiveOutputStream(output).use { zip ->
                    for (entry in entries) {
                        current = entry.item.name
                        if (entry.item.isDirectory) {
                            zip.putArchiveEntry(ZipArchiveEntry(entry.name + "/"))
                            zip.closeArchiveEntry()
                            continue
                        }
                        publish(TransferStatus.RUNNING)
                        val zipEntry = ZipArchiveEntry(entry.name)
                        if (entry.item.lastModifiedMillis > 0) zipEntry.time = entry.item.lastModifiedMillis
                        zip.putArchiveEntry(zipEntry)
                        repository.openInput(entry.item).getOrThrow().use { input -> pump(input, zip) }
                        zip.closeArchiveEntry()
                        doneFiles++
                    }
                }
            } catch (e: Exception) {
                withContext(NonCancellable) { removeQuietly(request.targetDirectory, name) }
                throw e
            }
        }

        private fun defaultArchiveName(): String {
            val first = request.items.firstOrNull()?.name.orEmpty()
            return if (request.items.size == 1) first.substringBeforeLast('.', first).ifEmpty { first } else "archive"
        }

        private suspend fun collect(item: FileItem, path: String, into: MutableList<Entry>) {
            into.add(Entry(path, item))
            if (item.isDirectory) {
                repository.listFiles(item.path).getOrThrow().forEach { collect(it, "$path/${it.name}", into) }
            }
        }

        // ---- extract --------------------------------------------------------------------------------------

        private suspend fun extractAll() {
            totalBytes = request.items.sumOf { it.sizeBytes }
            totalFiles = request.items.size
            publish(TransferStatus.RUNNING)
            for (archive in request.items) {
                val format = ArchiveFormat.of(archive.name) ?: throw ArchiveFormatNotSupported(archive.name)
                if (format == ArchiveFormat.SEVEN_Z) extractThroughRepository(archive) else extract(archive, format)
                doneFiles++
            }
        }

        private suspend fun extract(archive: FileItem, format: ArchiveFormat) {
            current = archive.name
            val taken = repository.listFiles(request.targetDirectory).getOrThrow().map { it.name.lowercase() }.toSet()
            val folderName = uniqueName(format.baseName(archive.name)) { it.lowercase() in taken }
            val root = repository.createDirectory(request.targetDirectory, folderName).getOrThrow()
            val baseBytes = doneBytes
            try {
                val counting = CountingInputStream(repository.openInput(archive).getOrThrow())
                counting.use {
                    val stream: ArchiveInputStream<*> = when (format) {
                        ArchiveFormat.ZIP -> ZipArchiveInputStream(counting)
                        ArchiveFormat.TAR -> TarArchiveInputStream(counting)
                        ArchiveFormat.TAR_GZ -> TarArchiveInputStream(GzipCompressorInputStream(counting))
                        ArchiveFormat.SEVEN_Z -> throw IOException("7z archives are read through the archive repository")
                    }
                    val dirs = HashMap<String, String>()
                    dirs[""] = root.path
                    while (true) {
                        val entry: ArchiveEntry = stream.nextEntry ?: break
                        if (entry is TarArchiveEntry && (entry.isSymbolicLink || entry.isLink)) continue
                        val parts = safeSegments(entry.name)
                        if (parts.isEmpty()) continue
                        if (entry.isDirectory) {
                            ensureDirectory(parts, dirs)
                            continue
                        }
                        val parent = ensureDirectory(parts.dropLast(1), dirs)
                        val fileName = parts.last()
                        current = fileName
                        val mime = MimeTypes.fromName(fileName) ?: MimeTypes.OCTET_STREAM
                        repository.openOutput(parent, fileName, mime, overwrite = true).getOrThrow().use { output ->
                            pump(stream, output) { doneBytes = baseBytes + counting.count }
                        }
                        doneBytes = baseBytes + counting.count
                        publish(TransferStatus.RUNNING)
                    }
                }
                doneBytes = baseBytes + archive.sizeBytes
            } catch (e: Exception) {
                withContext(NonCancellable) { removeQuietly(request.targetDirectory, folderName) }
                throw e
            }
        }

        /**
         * 7z needs random access, so it cannot be streamed like the others: the archive repository (which caches a local
         * copy) presents it as a folder and this copies that folder out. Progress counts unpacked bytes.
         */
        private suspend fun extractThroughRepository(archive: FileItem) {
            current = archive.name
            val taken = repository.listFiles(request.targetDirectory).getOrThrow().map { it.name.lowercase() }.toSet()
            val folderName = uniqueName(ArchiveFormat.SEVEN_Z.baseName(archive.name)) { it.lowercase() in taken }
            val source = ArchivePaths.rootOf(archive.path)
            val unpacked = sizeOf(source)
            totalBytes += unpacked - archive.sizeBytes
            publish(TransferStatus.RUNNING)
            val root = repository.createDirectory(request.targetDirectory, folderName).getOrThrow()
            try {
                copyTree(source, root.path)
            } catch (e: Exception) {
                withContext(NonCancellable) { removeQuietly(request.targetDirectory, folderName) }
                throw e
            }
        }

        private suspend fun sizeOf(dir: String): Long =
            repository.listFiles(dir).getOrThrow().sumOf { if (it.isDirectory) sizeOf(it.path) else it.sizeBytes }

        private suspend fun copyTree(dir: String, targetDir: String) {
            for (child in repository.listFiles(dir).getOrThrow()) {
                if (child.isDirectory) {
                    copyTree(child.path, repository.createDirectory(targetDir, child.name).getOrThrow().path)
                    continue
                }
                current = child.name
                val mime = MimeTypes.fromName(child.name) ?: MimeTypes.OCTET_STREAM
                repository.openInput(child).getOrThrow().use { input ->
                    repository.openOutput(targetDir, child.name, mime, overwrite = true).getOrThrow().use { output ->
                        pump(input, output, countBytes = true)
                    }
                }
                publish(TransferStatus.RUNNING)
            }
        }

        private suspend fun ensureDirectory(parts: List<String>, dirs: MutableMap<String, String>): String {
            var key = ""
            var path = dirs.getValue("")
            for (part in parts) {
                key = if (key.isEmpty()) part else "$key/$part"
                path = dirs[key] ?: repository.createDirectory(path, part).getOrThrow().path.also { dirs[key] = it }
            }
            return path
        }

        // ---- shared ---------------------------------------------------------------------------------------

        /** Copies [input] to [output] in chunks, honouring cancellation and the pause gate between chunks. */
        private suspend fun pump(
            input: InputStream,
            output: OutputStream,
            countBytes: Boolean = request.operation == OperationType.COMPRESS,
            onChunk: () -> Unit = {},
        ) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                gate.awaitResumed()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (countBytes) doneBytes += read
                onChunk()
                tick()
            }
        }

        private suspend fun removeQuietly(dir: String, name: String) {
            try {
                repository.listFiles(dir).getOrNull()?.firstOrNull { it.name == name }?.let { repository.delete(listOf(it)) }
            } catch (ignored: Exception) {
                // Best effort: the original failure is what the caller needs to see.
            }
        }

        private fun snapshot(status: TransferStatus, error: String? = null) = TransferProgress(
            currentName = current,
            processedBytes = doneBytes,
            totalBytes = totalBytes,
            processedFiles = doneFiles,
            totalFiles = totalFiles,
            bytesPerSecond = meter.record(doneBytes),
            status = status,
            error = error,
        )

        private fun tick() {
            val now = clockMillis()
            if (now - lastEmitMillis < EMIT_INTERVAL_MILLIS) return
            lastEmitMillis = now
            out.trySend(snapshot(TransferStatus.RUNNING))
        }

        private suspend fun publish(status: TransferStatus, error: String? = null) {
            lastEmitMillis = clockMillis()
            out.send(snapshot(status, error))
        }
    }

    private class Entry(val name: String, val item: FileItem)

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val EMIT_INTERVAL_MILLIS = 250L

        /**
         * Splits an archive entry name into folder/file names that stay inside the extraction folder.
         * Throws on `..` (zip-slip); leading slashes and `.` are dropped.
         */
        fun safeSegments(entryName: String): List<String> {
            val parts = entryName.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.any { it == ".." }) throw IOException("Unsafe path in archive: $entryName")
            return parts
        }
    }
}
