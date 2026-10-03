package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.ConflictDecision
import com.qtekfun.fexplo.core.model.ConflictResolution
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.data.repository.LocalFileSystemRepository
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import java.io.OutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
        engine: TransferEngine = this.engine,
        verify: Boolean = false,
    ): List<TransferProgress> {
        val items = sources.map { repo.stat(it.path).getOrThrow() }
        return engine.execute(TransferRequest(op, items, dst.path, verify), resolver).toList()
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

    // --- big files: temporary name + atomic rename -------------------------------------------

    private fun bigFileEngine(repository: FileSystemRepository = repo) =
        TransferEngine(repository, FileStreamCopier(), bigFileBytes = 10)

    @Test
    fun `big files are written under a temporary name and end up complete`() = runTest {
        val source = File(src, "footage.bin").apply { writeBytes(ByteArray(5_000) { (it % 251).toByte() }) }

        val progress = run(OperationType.COPY, source, engine = bigFileEngine())

        assertEquals(TransferStatus.COMPLETED, progress.last().status)
        assertArrayEquals(source.readBytes(), File(dst, "footage.bin").readBytes())
        assertEquals(listOf("footage.bin"), dst.list()!!.toList())
    }

    @Test
    fun `big file overwrite replaces the old file only once the copy is complete`() = runTest {
        File(src, "a.bin").writeBytes(ByteArray(100) { 1 })
        File(dst, "a.bin").writeBytes(ByteArray(300) { 2 })

        run(OperationType.COPY, File(src, "a.bin"), resolver = decision(ConflictResolution.OVERWRITE), engine = bigFileEngine())

        assertEquals(100, File(dst, "a.bin").length())
        assertEquals(listOf("a.bin"), dst.list()!!.toList())
    }

    /** Fails after a few bytes, like a full disk or a dropped connection. */
    private inner class FailingRepository : FileSystemRepository by repo {
        override suspend fun openOutput(
            parentUriOrPath: String,
            name: String,
            mimeType: String,
            overwrite: Boolean,
        ): Result<OutputStream> = repo.openOutput(parentUriOrPath, name, mimeType, overwrite).map { real ->
            object : OutputStream() {
                private var written = 0
                override fun write(b: Int) = real.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (written > 1_000) throw java.io.IOException("No space left on device")
                    written += len
                    real.write(b, off, len)
                }
                override fun close() = real.close()
            }
        }
    }

    @Test
    fun `a failing big copy leaves no partial file and keeps the source of a move`() = runTest {
        val source = File(src, "big.bin").apply { writeBytes(ByteArray(300_000) { 7 }) }
        val failing = TransferEngine(FailingRepository(), FileStreamCopier(bufferSize = 512), bigFileBytes = 10)

        val progress = run(OperationType.CUT, source, engine = failing)

        assertEquals(TransferStatus.FAILED, progress.last().status)
        assertTrue(source.exists())
        assertEquals(emptyList<String>(), dst.list()!!.toList())
    }

    // --- verification --------------------------------------------------------------------------

    @Test
    fun `verification passes on an intact copy and counts both passes in the progress`() = runTest {
        val source = File(src, "v.bin").apply { writeBytes(ByteArray(10_000) { (it % 13).toByte() }) }

        val progress = run(OperationType.COPY, source, verify = true)

        val last = progress.last()
        assertEquals(TransferStatus.COMPLETED, last.status)
        assertEquals(20_000L, last.totalBytes)
        assertEquals(20_000L, last.processedBytes)
    }

    /** Silently corrupts the written data. */
    private inner class CorruptingRepository : FileSystemRepository by repo {
        override suspend fun openOutput(
            parentUriOrPath: String,
            name: String,
            mimeType: String,
            overwrite: Boolean,
        ): Result<OutputStream> = repo.openOutput(parentUriOrPath, name, mimeType, overwrite).map { real ->
            object : OutputStream() {
                override fun write(b: Int) = real.write(b xor 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    val copy = b.copyOfRange(off, off + len)
                    copy[0] = (copy[0].toInt() xor 1).toByte()
                    real.write(copy, 0, len)
                }
                override fun close() = real.close()
            }
        }
    }

    @Test
    fun `verification catches a corrupted copy, removes it and keeps the original of a move`() = runTest {
        val source = File(src, "c.bin").apply { writeBytes(ByteArray(10_000) { 5 }) }
        val corrupting = TransferEngine(CorruptingRepository(), FileStreamCopier())

        val progress = run(OperationType.CUT, source, engine = corrupting, verify = true)

        assertEquals(TransferStatus.FAILED, progress.last().status)
        assertTrue(source.exists())
        assertFalse(File(dst, "c.bin").exists())
    }
}
