package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.AnalysisProgress
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SizeAnalyzerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val repo by lazy { LocalFileSystemRepository(tmp.root, "Internal") { null } }

    private fun file(path: String, bytes: Int): File = File(tmp.root, path).apply {
        parentFile.mkdirs()
        writeBytes(ByteArray(bytes))
    }

    @Test
    fun `totals and counts are exact and entries come biggest first`() = runTest {
        file("a.bin", 100)
        file("sub/b.bin", 300)
        file("sub/deep/c.bin", 50)
        File(tmp.root, "empty").mkdirs()

        val analysis = SizeAnalyzer(repo).analyze(tmp.root.path).getOrThrow()

        val root = analysis.root
        assertEquals(450L, root.bytes)
        assertEquals(3, root.files)
        assertEquals(listOf("sub", "a.bin", "empty"), root.children!!.map { it.item.name })
        val sub = root.children!!.first()
        assertEquals(350L, sub.bytes)
        assertEquals(2, sub.files)
        assertEquals(listOf("b.bin", "deep"), sub.children!!.map { it.item.name })
        assertEquals(0, analysis.unreadable)
    }

    @Test
    fun `a folder with more entries than the cap keeps the biggest and folds the rest with exact totals`() = runTest {
        for (size in 1..7) file("many/f$size.bin", size)

        val many = SizeAnalyzer(repo, maxChildren = 3).analyze(File(tmp.root, "many").path).getOrThrow().root

        assertEquals(28L, many.bytes)
        assertEquals(7, many.files)
        val children = many.children!!
        assertEquals(4, children.size)
        assertEquals(listOf(7L, 6L, 5L), children.take(3).map { it.bytes })
        val other = children.last()
        assertTrue(other.isOther)
        assertEquals(4, other.folded)
        assertEquals(10L, other.bytes)
        assertEquals(4, other.files)
        assertEquals(many.bytes, children.sumOf { it.bytes })
    }

    @Test
    fun `an unreadable folder is counted and does not fail the analysis`() = runTest {
        file("ok.bin", 10)
        val locked = File(tmp.root, "locked").apply { mkdirs() }
        File(locked, "x.bin").writeBytes(ByteArray(99))
        locked.setReadable(false)
        try {
            assumeFalse("running as a user that ignores permissions", locked.canRead())

            val analysis = SizeAnalyzer(repo).analyze(tmp.root.path).getOrThrow()

            assertEquals(1, analysis.unreadable)
            assertEquals(10L, analysis.root.bytes)
        } finally {
            locked.setReadable(true)
        }
    }

    @Test
    fun `analysis can be cancelled while a folder is being listed`() = runTest {
        class Stuck(inner: FileSystemRepository) : FileSystemRepository by inner {
            override suspend fun listFiles(uriOrPath: String): Result<List<com.qtekfun.ultimatefiles.core.model.FileItem>> = awaitCancellation()
        }
        val job = launch { SizeAnalyzer(Stuck(repo)).analyze(tmp.root.path) }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `a deep tree stops at the depth limit and says so`() = runTest {
        file("a/b/c/d/e/deep.bin", 500)
        file("a/top.bin", 7)

        val analysis = SizeAnalyzer(repo, maxDepth = 3).analyze(tmp.root.path).getOrThrow()

        // root (0), a (1), b (2) and c (3): c is as deep as it goes, so what is below it is not counted.
        assertEquals(1, analysis.unreadable)
        assertEquals(7L, analysis.root.bytes)
    }

    @Test
    fun `progress is throttled and the last report is complete`() = runTest {
        for (i in 1..5) file("p/f$i.bin", 10)
        val reports = mutableListOf<AnalysisProgress>()

        SizeAnalyzer(repo, clockMillis = { 1_000L }).analyze(tmp.root.path) { reports += it }.getOrThrow()

        assertEquals("the first report and the final one only", 2, reports.size)
        assertEquals(5, reports.last().files)
        assertEquals(50L, reports.last().bytes)

        var now = 0L
        val frequent = mutableListOf<AnalysisProgress>()
        SizeAnalyzer(repo, clockMillis = { now += SizeAnalyzer.PROGRESS_INTERVAL_MILLIS; now }).analyze(tmp.root.path) { frequent += it }.getOrThrow()
        assertTrue(frequent.size > reports.size)
    }

    @Test
    fun `something that is not an existing folder fails`() = runTest {
        val plain = file("plain.bin", 1)

        assertTrue(SizeAnalyzer(repo).analyze(plain.path).isFailure)
        assertTrue(SizeAnalyzer(repo).analyze(File(tmp.root, "missing").path).isFailure)
        assertNotNull(SizeAnalyzer(repo).analyze(tmp.root.path).getOrNull())
    }

    @Test
    fun `only folders on the device can be analysed`() {
        assertTrue(SizeAnalyzer.supports("/storage/emulated/0/DCIM"))
        assertTrue(SizeAnalyzer.supports("content://com.android.externalstorage.documents/tree/1234-ABCD%3A"))
        assertFalse(SizeAnalyzer.supports("dav://account/Photos"))
        assertFalse(SizeAnalyzer.supports("sftp://account/home"))
        assertFalse(SizeAnalyzer.supports("smb://account/share"))
        assertFalse(SizeAnalyzer.supports(ArchivePaths.rootOf("/storage/emulated/0/a.zip")))
    }
}
