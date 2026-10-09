package com.qtekfun.ultimatefiles.domain.transfer

import com.qtekfun.ultimatefiles.core.model.ConnectionProblem
import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.network.ConnectionProblems
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth
import com.qtekfun.ultimatefiles.domain.history.HistoryEntry
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRecorder
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.ConflictResolver
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException

/** A copy that fails because a server cannot be used says so, and the account is marked until it works again. */
class ConnectionReportTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var local: LocalFileSystemRepository
    private lateinit var dst: File

    /** Reading anything under `dav://` goes through [onRead]; everything else is the device's own storage. */
    private inner class Servers(private val onRead: () -> Result<InputStream>) : FileSystemRepository by local {
        override suspend fun openInput(item: FileItem): Result<InputStream> =
            if (item.path.startsWith("dav://")) onRead() else local.openInput(item)
    }

    private class MemoryHistory : TransferHistoryRepository {
        val list = MutableStateFlow(emptyList<HistoryEntry>())
        override val entries: Flow<List<HistoryEntry>> = list
        override suspend fun add(entry: HistoryEntry) { list.value = listOf(entry) + list.value }
        override suspend fun clear() { list.value = emptyList() }
    }

    @Before
    fun setUp() {
        local = LocalFileSystemRepository(tmp.root, "Internal") { null }
        dst = tmp.newFolder("dst")
    }

    private fun remote(name: String = "a.txt") = FileItem("dav://nas/$name", name, false, 3, 0L, "text/plain")

    private fun request(item: FileItem) = TransferRequest(OperationType.COPY, listOf(item), dst.path)

    private suspend fun lastProgress(engine: TransferEngine, request: TransferRequest): TransferProgress =
        engine.execute(request, ConflictResolver { _, _ -> error("no conflict") }).toList().last()

    @Test
    fun `a copy from a server that times out fails with the reason and the account`() = runBlocking {
        val engine = TransferEngine(Servers { Result.failure(SocketTimeoutException("timeout")) }, FileStreamCopier(), classifier = ConnectionProblems)

        val last = lastProgress(engine, request(remote()))

        assertEquals(TransferStatus.FAILED, last.status)
        assertEquals(ConnectionProblem(ConnectionProblemKind.NO_RESPONSE, "nas", null), last.connectionProblem)
    }

    @Test
    fun `a failure that is not about the connection carries no problem`() = runBlocking {
        val engine = TransferEngine(Servers { Result.failure(IOException("Already exists: a.txt")) }, FileStreamCopier(), classifier = ConnectionProblems)

        val last = lastProgress(engine, request(remote()))

        assertEquals(TransferStatus.FAILED, last.status)
        assertNull(last.connectionProblem)
        assertEquals("Already exists: a.txt", last.error)
    }

    @Test
    fun `a copy that only touches this device never carries a connection problem`() = runBlocking {
        val file = File(tmp.newFolder("src"), "a.txt").apply { writeText("abc") }
        val broken = object : FileSystemRepository by local {
            override suspend fun openInput(item: FileItem): Result<InputStream> = Result.failure(SocketTimeoutException("timeout"))
        }
        val engine = TransferEngine(broken, FileStreamCopier(), classifier = ConnectionProblems)

        val last = lastProgress(engine, request(local.stat(file.path).getOrThrow()))

        assertEquals(TransferStatus.FAILED, last.status)
        assertNull(last.connectionProblem)
    }

    @Test
    fun `the coordinator marks the account and announces the failure, and a later success clears the mark`() = runBlocking {
        val health = ConnectionHealth()
        var fail = true
        val repo = Servers { if (fail) Result.failure(SocketTimeoutException("timeout")) else Result.success(ByteArrayInputStream("abc".toByteArray())) }
        val history = MemoryHistory()
        val coordinator = TransferCoordinator(
            TransferEngine(repo, FileStreamCopier(), classifier = ConnectionProblems),
            TransferHistoryRecorder(history, repo, accountLabelOf = { "Casa NAS" }),
            health = health,
        ) { }
        val announced = CoroutineScope(Dispatchers.Default).launch { }
        announced.cancel()
        var seen: ConnectionProblem? = null
        val collector = CoroutineScope(Dispatchers.Default).launch { seen = coordinator.connectionProblems.first() }
        kotlinx.coroutines.delay(100)

        coordinator.run(request(remote()))
        withTimeout(2_000) { while (seen == null) kotlinx.coroutines.delay(20) }
        collector.cancel()

        assertEquals(ConnectionProblemKind.NO_RESPONSE, seen?.kind)
        assertEquals(ConnectionProblemKind.NO_RESPONSE, health.problems.value["nas"]?.kind)
        val entry = history.list.value.single()
        assertEquals(TransferStatus.FAILED, entry.status)
        assertEquals("connection:NO_RESPONSE:Casa NAS", entry.error)

        fail = false
        val ok = coordinator.run(request(remote("b.txt")))
        assertEquals(TransferStatus.COMPLETED, ok?.status)
        assertTrue("the account works again", health.problems.value.isEmpty())
        assertNotNull(history.list.value.first().finishedAtMillis)
    }
}
