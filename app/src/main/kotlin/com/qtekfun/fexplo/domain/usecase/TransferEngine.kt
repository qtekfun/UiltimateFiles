package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.ConflictDecision
import com.qtekfun.fexplo.core.model.ConflictResolution
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.core.util.MimeTypes
import com.qtekfun.fexplo.core.util.TransferSpeedMeter
import com.qtekfun.fexplo.core.util.uniqueName
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import com.qtekfun.fexplo.domain.transfer.PauseGate
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Decides what to do when the destination already holds an entry with the same name. */
fun interface ConflictResolver {
    suspend fun resolve(source: FileItem, existing: FileItem): ConflictDecision
}

/**
 * Executes a copy/move batch through [FileSystemRepository], recursing into directories.
 * Moves are copy + delete so they work across volumes and backends.
 */
class TransferEngine(
    private val repository: FileSystemRepository,
    private val copier: StreamCopier,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    /** Files at least this big are written under a temporary name and renamed once complete. */
    private val bigFileBytes: Long = DEFAULT_BIG_FILE_BYTES,
) {
    /** Pausing this gate suspends the running copy (and verification) between chunks. */
    val pauseGate = PauseGate()

    /**
     * Emits throttled progress snapshots (latest wins) and finishes with a terminal status
     * ([TransferStatus.COMPLETED] or [TransferStatus.FAILED]). Cancelling the collector aborts
     * the transfer and removes the partially written file.
     */
    fun execute(request: TransferRequest, resolver: ConflictResolver): Flow<TransferProgress> =
        channelFlow { Run(request, resolver, this).run() }
            .conflate()
            .flowOn(Dispatchers.IO)

    private inner class Run(
        private val request: TransferRequest,
        private val resolver: ConflictResolver,
        private val out: ProducerScope<TransferProgress>,
    ) {
        private val isMove = request.operation == OperationType.CUT
        private val meter = TransferSpeedMeter(clockMillis)
        private val listings = HashMap<String, List<FileItem>>()
        private var stickyDecision: ConflictDecision? = null
        private var totalBytes = 0L
        private var totalFiles = 0
        private var doneBytes = 0L
        private var doneFiles = 0
        private var current = ""
        private var lastEmitMillis = 0L

        suspend fun run() {
            try {
                request.items.forEach { measure(it) }
                publish(TransferStatus.RUNNING)
                request.items.forEach { transfer(it, request.targetDirectory) }
                publish(TransferStatus.COMPLETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(TransferStatus.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }

        private suspend fun measure(item: FileItem) {
            if (item.isDirectory) {
                children(item.path).forEach { measure(it) }
            } else {
                totalFiles++
                totalBytes += weight(item.sizeBytes)
            }
        }

        /** With verification every byte is processed twice: written, then read back. */
        private fun weight(size: Long) = if (request.verify) size * 2 else size

        /** Returns true when everything under [item] was transferred (nothing skipped). */
        private suspend fun transfer(item: FileItem, targetDir: String): Boolean {
            val existing = findExisting(targetDir, item.name)
            return if (item.isDirectory) transferDirectory(item, targetDir, existing)
            else transferFile(item, targetDir, existing)
        }

        private suspend fun transferDirectory(item: FileItem, targetDir: String, existing: FileItem?): Boolean {
            // Snapshot first so a destination created inside the source is never walked.
            val sourceChildren = children(item.path)
            val destination: FileItem = when {
                existing != null && existing.isDirectory -> existing // merge into the existing folder
                existing == null -> createDirectory(targetDir, item.name)
                else -> when (decide(item, existing).resolution) {
                    ConflictResolution.SKIP -> {
                        skip(item)
                        return false
                    }
                    ConflictResolution.RENAME -> createDirectory(targetDir, freeName(targetDir, item.name))
                    ConflictResolution.OVERWRITE -> {
                        repository.delete(listOf(existing)).getOrThrow()
                        listings.remove(targetDir)
                        createDirectory(targetDir, item.name)
                    }
                }
            }
            var complete = true
            for (child in sourceChildren) {
                if (!transfer(child, destination.path)) complete = false
            }
            if (isMove && complete) repository.delete(listOf(item)).getOrThrow()
            return complete
        }

        private suspend fun transferFile(item: FileItem, targetDir: String, existing: FileItem?): Boolean {
            var name = item.name
            var overwrite = false
            if (existing != null) {
                // A folder cannot be overwritten by a file: rename without asking.
                val resolution = if (existing.isDirectory) ConflictResolution.RENAME else decide(item, existing).resolution
                when (resolution) {
                    ConflictResolution.SKIP -> {
                        skip(item)
                        return false
                    }
                    ConflictResolution.RENAME -> name = freeName(targetDir, item.name)
                    ConflictResolution.OVERWRITE -> overwrite = true
                }
            }
            current = item.name
            publish(TransferStatus.RUNNING)
            val big = item.sizeBytes >= bigFileBytes
            // Big files: a half-written file must never look like the real one.
            val writeName = if (big) name + PART_SUFFIX else name
            try {
                val sourceHash = copyFile(item, targetDir, writeName, overwrite = big || overwrite)
                if (big) finalizePart(targetDir, writeName, name)
                if (sourceHash != null) verify(item, targetDir, name, sourceHash)
            } catch (e: Exception) {
                withContext(NonCancellable) { discardPartial(targetDir, writeName) }
                throw e
            }
            listings.remove(targetDir)
            doneFiles++
            // The source of a move is only deleted after the copy (and verification) succeeded.
            if (isMove) repository.delete(listOf(item)).getOrThrow()
            return true
        }

        /** Replaces any existing [finalName] with the finished temporary file. */
        private suspend fun finalizePart(targetDir: String, partName: String, finalName: String) {
            listings.remove(targetDir)
            val part = findExisting(targetDir, partName) ?: throw IOException("Temporary file $partName vanished")
            findExisting(targetDir, finalName)?.let { repository.delete(listOf(it)).getOrThrow() }
            repository.rename(part, finalName).getOrThrow()
            listings.remove(targetDir)
        }

        /** Reads the copy back and compares its SHA-256 with the one computed while copying. */
        private suspend fun verify(source: FileItem, targetDir: String, name: String, expected: ByteArray) {
            publish(TransferStatus.VERIFYING)
            listings.remove(targetDir)
            val copy = findExisting(targetDir, name) ?: throw IOException("Copy of $name not found")
            val digest = MessageDigest.getInstance("SHA-256")
            repository.openInput(copy).getOrThrow().use { input ->
                val buffer = ByteArray(VERIFY_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    pauseGate.awaitResumed()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    doneBytes += read
                    tick(TransferStatus.VERIFYING)
                }
            }
            if (!MessageDigest.isEqual(expected, digest.digest())) {
                repository.delete(listOf(copy))
                throw IOException("Verification failed for ${source.name}: the copy does not match the original")
            }
        }

        /** Returns the SHA-256 of what was copied when verification is on, else null. */
        private suspend fun copyFile(item: FileItem, targetDir: String, name: String, overwrite: Boolean): ByteArray? {
            val mime = item.mimeType ?: MimeTypes.fromName(name) ?: MimeTypes.OCTET_STREAM
            val digest = if (request.verify) MessageDigest.getInstance("SHA-256") else null
            repository.openInput(item).getOrThrow().use { input ->
                repository.openOutput(targetDir, name, mime, overwrite).getOrThrow().use { output ->
                    copier.copy(input, output, item.sizeBytes, digest, pauseGate) { bytes ->
                        doneBytes += bytes
                        tick()
                    }
                }
            }
            return digest?.digest()
        }

        private suspend fun discardPartial(targetDir: String, name: String) {
            try {
                listings.remove(targetDir)
                findExisting(targetDir, name)?.let { repository.delete(listOf(it)) }
            } catch (ignored: Exception) {
                // Best effort: the original failure is what the caller needs to see.
            }
        }

        private suspend fun skip(item: FileItem) {
            val (bytes, files) = weigh(item)
            doneBytes += bytes
            doneFiles += files
            publish(TransferStatus.RUNNING)
        }

        private suspend fun weigh(item: FileItem): Pair<Long, Int> =
            if (item.isDirectory) {
                children(item.path).fold(0L to 0) { (bytes, files), child ->
                    val (b, f) = weigh(child)
                    bytes + b to files + f
                }
            } else {
                weight(item.sizeBytes) to 1
            }

        private suspend fun decide(item: FileItem, existing: FileItem): ConflictDecision {
            stickyDecision?.let { return it }
            publish(TransferStatus.WAITING_CONFLICT)
            val decision = resolver.resolve(item, existing)
            if (decision.applyToAll) stickyDecision = decision
            return decision
        }

        private suspend fun createDirectory(parent: String, name: String): FileItem {
            val created = repository.createDirectory(parent, name).getOrThrow()
            listings.remove(parent)
            return created
        }

        private suspend fun freeName(dir: String, name: String): String {
            val taken = children(dir).map { it.name.lowercase() }.toSet()
            return uniqueName(name) { it.lowercase() in taken }
        }

        private suspend fun children(dir: String): List<FileItem> =
            listings[dir] ?: repository.listFiles(dir).getOrThrow().also { listings[dir] = it }

        // Case-insensitive: FAT/exFAT volumes (typical for USB drives) do not distinguish case.
        private suspend fun findExisting(dir: String, name: String): FileItem? =
            children(dir).firstOrNull { it.name.equals(name, ignoreCase = true) }

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

        /** Non-suspending, throttled update used from the byte-copy callback. */
        private fun tick(status: TransferStatus = TransferStatus.RUNNING) {
            val now = clockMillis()
            if (now - lastEmitMillis < EMIT_INTERVAL_MILLIS) return
            lastEmitMillis = now
            out.trySend(snapshot(status))
        }

        private suspend fun publish(status: TransferStatus, error: String? = null) {
            lastEmitMillis = clockMillis()
            out.send(snapshot(status, error))
        }
    }

    companion object {
        /** Suffix of the temporary name given to big files while they are being written. */
        const val PART_SUFFIX = ".fexplo-part"
        const val DEFAULT_BIG_FILE_BYTES = 64L * 1024 * 1024
        private const val EMIT_INTERVAL_MILLIS = 250L
        private const val VERIFY_BUFFER_SIZE = 1024 * 1024
    }
}
