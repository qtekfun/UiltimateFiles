package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.ConflictDecision
import com.qtekfun.ultimatefiles.core.model.ConflictResolution
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveEngineTest {
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

    private val noConflicts = ConflictResolver { _, _ -> ConflictDecision(ConflictResolution.SKIP) }

    private suspend fun run(op: OperationType, vararg sources: File, name: String? = null): List<TransferProgress> {
        val items = sources.map { repo.stat(it.path).getOrThrow() }
        return engine.execute(TransferRequest(op, items, dst.path, archiveName = name), noConflicts).toList()
    }

    private fun zipEntries(file: File): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = if (entry.isDirectory) null else zip.readBytes().decodeToString()
            }
        }
        return result
    }

    @Test
    fun `compresses files and folders into one zip and keeps the sources`() = runTest {
        val tree = File(src, "tree").apply { mkdirs() }
        File(tree, "a.txt").writeText("aaa")
        File(tree, "inner").mkdirs()
        File(tree, "inner/b.txt").writeText("bbbb")
        val single = File(src, "c.txt").apply { writeText("c") }

        val progress = run(OperationType.COMPRESS, tree, single, name = "pack")

        assertEquals(TransferStatus.COMPLETED, progress.last().status)
        assertEquals(5L, progress.last().processedBytes)
        val entries = zipEntries(File(dst, "pack.zip"))
        assertEquals("aaa", entries["tree/a.txt"])
        assertEquals("bbbb", entries["tree/inner/b.txt"])
        assertEquals("c", entries["c.txt"])
        assertTrue(entries.containsKey("tree/inner/"))
        assertTrue(File(src, "c.txt").exists())
    }

    @Test
    fun `never overwrites an existing archive`() = runTest {
        File(dst, "pack.zip").writeText("old")
        val file = File(src, "x.txt").apply { writeText("x") }

        run(OperationType.COMPRESS, file, name = "pack.zip")

        assertEquals("old", File(dst, "pack.zip").readText())
        assertTrue(File(dst, "pack (1).zip").exists() || dst.listFiles()!!.size == 2)
    }

    @Test
    fun `extracts a zip into a folder named after it`() = runTest {
        val zip = File(src, "docs.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("readme.txt")); it.write("hello".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("sub/deep/x.txt")); it.write("deep".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("empty/")); it.closeEntry()
        }

        val progress = run(OperationType.EXTRACT, zip)

        assertEquals(TransferStatus.COMPLETED, progress.last().status)
        assertEquals("hello", File(dst, "docs/readme.txt").readText())
        assertEquals("deep", File(dst, "docs/sub/deep/x.txt").readText())
        assertTrue(File(dst, "docs/empty").isDirectory)
    }

    @Test
    fun `extracts tar and tar gz`() = runTest {
        fun tar(out: java.io.OutputStream) = TarArchiveOutputStream(out).use { tar ->
            val data = "tar body".toByteArray()
            tar.putArchiveEntry(TarArchiveEntry("folder/t.txt").apply { size = data.size.toLong() })
            tar.write(data)
            tar.closeArchiveEntry()
        }
        val plain = File(src, "a.tar").also { tar(it.outputStream()) }
        val gz = File(src, "b.tar.gz").also { tar(GZIPOutputStream(it.outputStream())) }

        val progress = run(OperationType.EXTRACT, plain, gz)

        assertEquals(TransferStatus.COMPLETED, progress.last().status)
        assertEquals("tar body", File(dst, "a/folder/t.txt").readText())
        assertEquals("tar body", File(dst, "b/folder/t.txt").readText())
    }

    @Test
    fun `refuses entries that escape the folder and removes the partial result`() = runTest {
        val zip = File(src, "evil.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("ok.txt")); it.write("ok".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("../escaped.txt")); it.write("bad".toByteArray()); it.closeEntry()
        }

        val progress = run(OperationType.EXTRACT, zip)

        assertEquals(TransferStatus.FAILED, progress.last().status)
        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(dst, "escaped.txt").exists())
        assertFalse(File(dst, "evil").exists())
    }

    @Test
    fun `a file that is not a known archive fails with a message`() = runTest {
        val file = File(src, "notes.txt").apply { writeText("x") }

        val progress = run(OperationType.EXTRACT, file)

        assertEquals(TransferStatus.FAILED, progress.last().status)
        assertTrue(progress.last().error!!.contains("notes.txt"))
    }

    @Test
    fun `safeSegments drops dots and slashes and rejects parent references`() {
        assertEquals(listOf("a", "b.txt"), ArchiveEngine.safeSegments("/a/./b.txt"))
        assertEquals(listOf("a", "b"), ArchiveEngine.safeSegments("a\\b"))
        val failed = runCatching { ArchiveEngine.safeSegments("a/../../b") }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `format detection`() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.of("x.TGZ"))
        assertEquals("photos", ArchiveFormat.TAR_GZ.baseName("photos.tar.gz"))
        assertEquals(null, ArchiveFormat.of("movie.mkv"))
    }
}
