package com.qtekfun.fexplo.data.repository

import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.domain.history.HistoryEntry
import com.qtekfun.fexplo.domain.history.HistoryOperation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTransferHistoryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(time: Long, name: String = "f", error: String? = null, target: String? = "dest") = HistoryEntry(
        finishedAtMillis = time,
        operation = HistoryOperation.COPY,
        status = if (error == null) TransferStatus.COMPLETED else TransferStatus.FAILED,
        itemCount = 2,
        totalBytes = 1234,
        firstItemName = name,
        targetName = target,
        error = error,
    )

    @Test
    fun `starts empty and keeps newest first`() = runTest {
        val repo = FileTransferHistoryRepository(tmp.root.resolve("h.tsv"))
        assertTrue(repo.entries.first().isEmpty())

        repo.add(entry(1, "old"))
        repo.add(entry(2, "new"))

        assertEquals(listOf("new", "old"), repo.entries.first().map { it.firstItemName })
    }

    @Test
    fun `survives a restart and special characters`() = runTest {
        val file = tmp.root.resolve("h.tsv")
        val tricky = entry(5, "a\tb\nc\\d\\N", error = "boom\r\nnext", target = null)
        FileTransferHistoryRepository(file).add(tricky)

        val reloaded = FileTransferHistoryRepository(file).entries.first()

        assertEquals(listOf(tricky), reloaded)
        assertNull(reloaded.first().targetName)
    }

    @Test
    fun `keeps only the most recent entries and can be cleared`() = runTest {
        val repo = FileTransferHistoryRepository(tmp.root.resolve("h.tsv"), maxEntries = 3)
        (1..5).forEach { repo.add(entry(it.toLong(), "f$it")) }

        assertEquals(listOf("f5", "f4", "f3"), repo.entries.first().map { it.firstItemName })

        repo.clear()
        assertTrue(FileTransferHistoryRepository(tmp.root.resolve("h.tsv")).entries.first().isEmpty())
    }

    @Test
    fun `damaged lines are skipped`() = runTest {
        val file = tmp.root.resolve("h.tsv")
        file.writeText("garbage\n" + FileTransferHistoryRepository.encode(entry(1, "ok")))

        assertEquals(listOf("ok"), FileTransferHistoryRepository(file).entries.first().map { it.firstItemName })
    }
}
