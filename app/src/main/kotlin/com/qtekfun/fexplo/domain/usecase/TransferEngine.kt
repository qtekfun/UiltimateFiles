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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
) {
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
                totalBytes += item.sizeBytes
            }
        }

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
            try {
                copyFile(item, targetDir, name, overwrite)
            } catch (e: Exception) {
                withContext(NonCancellable) { discardPartial(targetDir, name) }
                throw e
            }
            listings.remove(targetDir)
            doneFiles++
            if (isMove) repository.delete(listOf(item)).getOrThrow()
            return true
        }

        private suspend fun copyFile(item: FileItem, targetDir: String, name: String, overwrite: Boolean) {
            val mime = item.mimeType ?: MimeTypes.fromName(name) ?: MimeTypes.OCTET_STREAM
            repository.openInput(item).getOrThrow().use { input ->
                repository.openOutput(targetDir, name, mime, overwrite).getOrThrow().use { output ->
                    copier.copy(input, output) { bytes ->
                        doneBytes += bytes
                        tick()
                    }
                }
            }
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
                item.sizeBytes to 1
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

    private companion object {
        const val EMIT_INTERVAL_MILLIS = 250L
    }
}
