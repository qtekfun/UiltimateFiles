package com.qtekfun.ultimatefiles.domain.transfer

import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.domain.history.HistoryEntry
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRecorder
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRepository
import com.qtekfun.ultimatefiles.domain.usecase.ConflictResolver
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
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

class TransferJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    private class MemoryJournal(var stored: List<TransferRequest> = emptyList()) : TransferJournal {
        override fun load(): List<TransferRequest> = stored
        override fun save(requests: List<TransferRequest>) { stored = requests }
    }

    private class NoHistory : TransferHistoryRepository {
        override val entries: Flow<List<HistoryEntry>> = MutableStateFlow(emptyList())
        override suspend fun add(entry: HistoryEntry) = Unit
        override suspend fun clear() = Unit
    }

    private fun setUp(journal: TransferJournal): Pair<LocalFileSystemRepository, TransferCoordinator> {
        val repo = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val engine = TransferEngine(repo, FileStreamCopier())
        val coordinator = TransferCoordinator(engine, TransferHistoryRecorder(NoHistory(), repo), journal = journal) { }
        return repo to coordinator
    }

    @Test
    fun `an unfinished batch is in the journal and leaves it when it ends`() = runBlocking {
        val journal = MemoryJournal()
        val (repo, coordinator) = setUp(journal)
        val dst = tmp.newFolder("dst")
        val file = File(tmp.newFolder("src"), "a.txt").apply { writeText("hi") }
        val request = TransferRequest(OperationType.COPY, listOf(repo.stat(file.path).getOrThrow()), dst.path)

        coordinator.enqueue(request)
        assertEquals(listOf(request), journal.stored)

        assertTrue(coordinator.claimWorker())
        coordinator.run(coordinator.next()!!)
        assertTrue("finished work must not be offered again", journal.stored.isEmpty())
    }

    @Test
    fun `cancelling empties the journal`() = runBlocking {
        val journal = MemoryJournal()
        val (repo, coordinator) = setUp(journal)
        val dst = tmp.newFolder("dst")
        val file = File(tmp.newFolder("src"), "a.txt").apply { writeText("hi") }
        coordinator.enqueue(TransferRequest(OperationType.COPY, listOf(repo.stat(file.path).getOrThrow()), dst.path))

        coordinator.cancelAll()

        assertTrue(journal.stored.isEmpty())
    }

    @Test
    fun `what a killed process left behind is offered and resumed without the vanished items`() = runBlocking {
        val src = tmp.newFolder("src")
        val kept = File(src, "kept.txt").apply { writeText("data") }
        val moved = File(src, "moved.txt").apply { writeText("gone") }
        val repoForItems = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val keptItem = repoForItems.stat(kept.path).getOrThrow()
        val movedItem = repoForItems.stat(moved.path).getOrThrow()
        moved.delete() // the previous run had already moved this one
        val dst = tmp.newFolder("dst")
        val journal = MemoryJournal(listOf(TransferRequest(OperationType.CUT, listOf(keptItem, movedItem), dst.path)))
        val (_, coordinator) = setUp(journal)

        coordinator.loadInterrupted()
        assertEquals(1, coordinator.interrupted.value.size)

        coordinator.restoreInterrupted()
        assertTrue(coordinator.interrupted.value.isEmpty())
        assertEquals("only the surviving item is queued again", listOf("kept.txt"), journal.stored.single().items.map { it.name })

        assertTrue(coordinator.claimWorker())
        coordinator.run(coordinator.next()!!)

        assertEquals("data", File(dst, "kept.txt").readText())
        assertFalse(kept.exists())
        assertTrue(journal.stored.isEmpty())
    }

    @Test
    fun `discarding forgets the interrupted batches`() = runBlocking {
        val journal = MemoryJournal(listOf(TransferRequest(OperationType.COPY, emptyList(), "/x")))
        val (_, coordinator) = setUp(journal)
        coordinator.loadInterrupted()
        assertEquals(1, coordinator.interrupted.value.size)

        coordinator.discardInterrupted()

        assertTrue(coordinator.interrupted.value.isEmpty())
        assertTrue(journal.stored.isEmpty())
    }
}
