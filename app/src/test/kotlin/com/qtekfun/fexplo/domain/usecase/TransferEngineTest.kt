package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.ConflictDecision
import com.qtekfun.fexplo.core.model.ConflictResolution
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.data.repository.LocalFileSystemRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransferEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var repo: LocalFileSystemRepository
    private lateinit var engine: TransferEngine
    private lateinit var src: File
    private lateinit var dst: File

    @Before
    fun setUp() {
        repo = LocalFileSystemRepository(tmp.root, "Internal") { null }
        engine = TransferEngine(repo, FileStreamCopier())
        src = tmp.newFolder("src")
        dst = tmp.newFolder("dst")
    }

    private suspend fun run(
        op: OperationType,
        vararg sources: File,
        resolver: ConflictResolver = ConflictResolver { _, _ -> error("no conflict expected") },
    ): List<TransferProgress> {
        val items = sources.map { repo.stat(it.path).getOrThrow() }
        return engine.execute(TransferRequest(op, items, dst.path), resolver).toList()
    }

    private fun decision(resolution: ConflictResolution, all: Boolean = false) =
        ConflictResolver { _, _ -> ConflictDecision(resolution, all) }

    @Test
    fun `copies a folder tree and keeps the source`() = runTest {
        val tree = File(src, "tree").apply { mkdirs() }
        File(tree, "a.txt").writeText("aaa")
        File(tree, "inner").mkdirs()
        File(tree, "inner/b.txt").writeText("bb")

        val progress = run(OperationType.COPY, tree)

        assertEquals("aaa", File(dst, "tree/a.txt").readText())
        assertEquals("bb", File(dst, "tree/inner/b.txt").readText())
        assertTrue(File(tree, "a.txt").exists())
        val last = progress.last()
        assertEquals(TransferStatus.COMPLETED, last.status)
        assertEquals(5L, last.processedBytes)
        assertEquals(2, last.processedFiles)
    }

    @Test
    fun `move removes the source`() = runTest {
        val tree = File(src, "tree").apply { mkdirs() }
        File(tree, "a.txt").writeText("aaa")

        run(OperationType.CUT, tree)

        assertEquals("aaa", File(dst, "tree/a.txt").readText())
        assertFalse(tree.exists())
    }

    @Test
    fun `rename resolution keeps both files`() = runTest {
        File(src, "a.txt").writeText("new")
        File(dst, "a.txt").writeText("old")

        run(OperationType.COPY, File(src, "a.txt"), resolver = decision(ConflictResolution.RENAME))

        assertEquals("old", File(dst, "a.txt").readText())
        assertEquals("new", File(dst, "a (1).txt").readText())
    }

    @Test
    fun `overwrite resolution replaces the destination`() = runTest {
        File(src, "a.txt").writeText("new")
        File(dst, "a.txt").writeText("old and longer")

        run(OperationType.COPY, File(src, "a.txt"), resolver = decision(ConflictResolution.OVERWRITE))

        assertEquals("new", File(dst, "a.txt").readText())
    }

    @Test
    fun `skipped files are kept at the source when moving`() = runTest {
        File(src, "a.txt").writeText("new")
        File(dst, "a.txt").writeText("old")

        val progress = run(OperationType.CUT, File(src, "a.txt"), resolver = decision(ConflictResolution.SKIP))

        assertEquals("old", File(dst, "a.txt").readText())
        assertTrue(File(src, "a.txt").exists())
        assertEquals(TransferStatus.COMPLETED, progress.last().status)
    }

    @Test
    fun `apply to all asks only once`() = runTest {
        File(src, "a.txt").writeText("1")
        File(src, "b.txt").writeText("2")
        File(dst, "a.txt").writeText("x")
        File(dst, "b.txt").writeText("y")
        var asked = 0
        val resolver = ConflictResolver { _, _ ->
            asked++
            ConflictDecision(ConflictResolution.SKIP, applyToAll = true)
        }

        run(OperationType.COPY, File(src, "a.txt"), File(src, "b.txt"), resolver = resolver)

        assertEquals(1, asked)
    }

    @Test
    fun `merging into an existing folder does not ask`() = runTest {
        File(src, "d").mkdirs()
        File(src, "d/new.txt").writeText("n")
        File(dst, "d").mkdirs()
        File(dst, "d/old.txt").writeText("o")

        run(OperationType.COPY, File(src, "d"))

        assertTrue(File(dst, "d/new.txt").exists())
        assertTrue(File(dst, "d/old.txt").exists())
    }

    @Test
    fun `missing source ends in FAILED`() = runTest {
        val item = repo.stat(File(src, "a.txt").also { it.writeText("1") }.path).getOrThrow()
        File(src, "a.txt").delete()

        val progress = engine.execute(TransferRequest(OperationType.COPY, listOf(item), dst.path)) { _, _ ->
            error("no conflict expected")
        }.toList()

        assertEquals(TransferStatus.FAILED, progress.last().status)
    }
}
