package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.domain.history.HistoryEntry
import com.qtekfun.ultimatefiles.domain.history.HistoryOperation
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps the last [maxEntries] operations in a small tab-separated file (one entry per line,
 * newest first). A plain file avoids pulling in a database for a few hundred rows.
 */
class FileTransferHistoryRepository(
    private val file: File,
    private val maxEntries: Int = 200,
) : TransferHistoryRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<HistoryEntry>?>(null)

    override val entries: Flow<List<HistoryEntry>> = flow {
        mutex.withLock { loadIfNeeded() }
        emitAll(state.filterNotNull())
    }

    override suspend fun add(entry: HistoryEntry) {
        mutex.withLock {
            val next = (listOf(entry) + loadIfNeeded()).take(maxEntries)
            state.value = next
            write(next)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            state.value = emptyList()
            write(emptyList())
        }
    }

    private suspend fun loadIfNeeded(): List<HistoryEntry> =
        state.value ?: withContext(Dispatchers.IO) { read() }.also { state.value = it }

    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::decode)
    }

    private suspend fun write(entries: List<HistoryEntry>) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(entries.joinToString("\n") { encode(it) })
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    internal companion object {
        fun encode(entry: HistoryEntry): String = listOf(
            entry.finishedAtMillis.toString(),
            entry.operation.name,
            entry.status.name,
            entry.itemCount.toString(),
            entry.totalBytes.toString(),
            Tsv.escape(entry.firstItemName),
            entry.targetName?.let(Tsv::escape) ?: Tsv.NULL,
            entry.error?.let(Tsv::escape) ?: Tsv.NULL,
        ).joinToString("\t")

        fun decode(line: String): HistoryEntry? = try {
            val f = line.split('\t')
            HistoryEntry(
                finishedAtMillis = f[0].toLong(),
                operation = HistoryOperation.valueOf(f[1]),
                status = TransferStatus.valueOf(f[2]),
                itemCount = f[3].toInt(),
                totalBytes = f[4].toLong(),
                firstItemName = Tsv.unescape(f[5]),
                targetName = f[6].takeIf { it != Tsv.NULL }?.let(Tsv::unescape),
                error = f[7].takeIf { it != Tsv.NULL }?.let(Tsv::unescape),
            )
        } catch (e: Exception) {
            null // A damaged line must not take the whole history down.
        }
    }
}
