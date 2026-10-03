package com.qtekfun.fexplo.domain.transfer

import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.data.repository.LocalFileSystemRepository
import com.qtekfun.fexplo.domain.history.HistoryEntry
import com.qtekfun.fexplo.domain.history.TransferHistoryRecorder
import com.qtekfun.fexplo.domain.history.TransferHistoryRepository
import com.qtekfun.fexplo.domain.usecase.ConflictResolver
import com.qtekfun.fexplo.domain.usecase.TransferEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransferPauseTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var repo: LocalFileSystemRepository
    private lateinit var engine: TransferEngine
    private lateinit var src: File
    private lateinit var dst: File

    @Before
    fun setUp() {
        repo = LocalFileSystemRepository(tmp.root, "Internal") { null }
        engine = TransferEngine(repo, FileStreamCopier(bufferSize = 4_096))
        src = tmp.newFolder("src")
        dst = tmp.newFolder("dst")
    }

    private suspend fun request(file: File) =
        TransferRequest(OperationType.COPY, listOf(repo.stat(file.path).getOrThrow()), dst.path)

    @Test
    fun `a paused copy moves no bytes until resumed and then finishes intact`() = runBlocking {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val file = File(src, "big.bin").apply { writeBytes(data) }
        engine.pauseGate.pause()

        val result = async { engine.execute(request(file), ConflictResolver { _, _ -> error("no conflict") }).toList() }
        delay(400)

        assertFalse("still running while paused", result.isCompleted)
        assertEquals("nothing written while paused", 0L, File(dst, "big.bin").length())

        engine.pauseGate.resume()
        val progress = result.await()

        assertEquals(TransferStatus.COMPLETED, progress.last().status)
        assertTrue(data.contentEquals(File(dst, "big.bin").readBytes()))
    }

    @Test
    fun `cancelling a paused copy does not hang`() = runBlocking {
        val file = File(src, "a.bin").apply { writeBytes(ByteArray(50_000)) }
        engine.pauseGate.pause()
        val job = async { engine.execute(request(file), ConflictResolver { _, _ -> error("no conflict") }).toList() }
        delay(200)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    private class MemoryHistory : TransferHistoryRepository {
        val list = MutableStateFlow(emptyList<HistoryEntry>())
        override val entries: Flow<List<HistoryEntry>> = list
        override suspend fun add(entry: HistoryEntry) { list.value = listOf(entry) + list.value }
        override suspend fun clear() { list.value = emptyList() }
    }

    @Test
    fun `coordinator exposes pause state, queued tasks and clears pause when the queue drains`() = runBlocking {
        val coordinator = TransferCoordinator(engine, TransferHistoryRecorder(MemoryHistory(), repo)) { }
        val file = File(src, "a.txt").apply { writeText("x") }

        coordinator.enqueue(request(file))
        assertEquals(1, coordinator.state.value.queued)
        assertEquals("a.txt", coordinator.state.value.queuedTasks.single().firstItemName)

        coordinator.pause()
        assertTrue(coordinator.state.value.paused)
        coordinator.togglePause()
        assertFalse(coordinator.state.value.paused)

        coordinator.pause()
        assertTrue(coordinator.claimWorker())
        assertTrue(coordinator.next() != null)
        assertNull(coordinator.next())
        assertFalse("a drained queue must not leave the next batch paused", coordinator.state.value.paused)
        assertFalse(engine.pauseGate.paused.value)
    }
}
