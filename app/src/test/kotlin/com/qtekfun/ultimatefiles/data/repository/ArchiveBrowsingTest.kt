package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveBrowsingTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var local: LocalFileSystemRepository
    private lateinit var router: FileSystemRepository
    private lateinit var archives: ArchiveFileSystemRepository
    private lateinit var dir: File

    @Before
    fun setUp() {
        local = LocalFileSystemRepository(tmp.root, "Internal") { null }
        archives = ArchiveFileSystemRepository({ router }, tmp.newFolder("cache"))
        router = RoutingFileSystemRepository(local, local, local, local, archives)
        dir = tmp.newFolder("files")
    }

    private fun zip(): File = File(dir, "docs.zip").also { file ->
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("readme.txt")); it.write("hello".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("sub/deep/x.txt")); it.write("deep".toByteArray()); it.closeEntry()
        }
    }

    private fun sevenZip(): File = File(dir, "pack.7z").also { file ->
        val a = tmp.newFile("a.txt").apply { writeText("seven a") }
        val b = tmp.newFile("b.txt").apply { writeText("seven b") }
        SevenZOutputFile(file).use { out ->
            for ((real, name) in listOf(a to "a.txt", b to "folder/b.txt")) {
                out.putArchiveEntry(out.createArchiveEntry(real, name))
                out.write(real.readBytes())
                out.closeArchiveEntry()
            }
        }
    }

    private fun tarGz(): File = File(dir, "t.tar.gz").also { file ->
        TarArchiveOutputStream(GZIPOutputStream(file.outputStream())).use { tar ->
            val body = "tar body".toByteArray()
            tar.putArchiveEntry(TarArchiveEntry("folder/t.txt").apply { size = body.size.toLong() })
            tar.write(body)
            tar.closeArchiveEntry()
        }
    }

    private suspend fun names(path: String) = router.listFiles(path).getOrThrow().map { it.name }.sorted()

    private suspend fun read(path: String) = router.openInput(router.stat(path).getOrThrow()).getOrThrow().use { it.readBytes().decodeToString() }

    @Test
    fun `a zip is browsable as folders`() = runTest {
        val root = ArchivePaths.rootOf(zip().path)

        assertEquals(listOf("readme.txt", "sub"), names(root))
        assertEquals(listOf("deep"), names(ArchivePaths.pathOf(zip().path, "sub")))
        assertEquals("deep", read(ArchivePaths.pathOf(zip().path, "sub/deep/x.txt")))
        assertEquals(5L, router.stat(ArchivePaths.pathOf(zip().path, "readme.txt")).getOrThrow().sizeBytes)
    }

    @Test
    fun `7z and tar gz are browsable too`() = runTest {
        val seven = ArchivePaths.rootOf(sevenZip().path)
        assertEquals(listOf("a.txt", "folder"), names(seven))
        assertEquals("seven b", read(ArchivePaths.pathOf(sevenZip().path, "folder/b.txt")))

        val tar = ArchivePaths.rootOf(tarGz().path)
        assertEquals(listOf("folder"), names(tar))
        assertEquals("tar body", read(ArchivePaths.pathOf(tarGz().path, "folder/t.txt")))
    }

    @Test
    fun `going up from the archive root leaves the archive`() = runTest {
        val file = zip()

        assertEquals(dir.path, router.parentOf(ArchivePaths.rootOf(file.path)))
        assertEquals(ArchivePaths.pathOf(file.path, "sub"), router.parentOf(ArchivePaths.pathOf(file.path, "sub/deep")))
        assertEquals(ArchivePaths.rootOf(file.path), router.parentOf(ArchivePaths.pathOf(file.path, "sub")))
    }

    @Test
    fun `archive contents are read-only`() = runTest {
        val root = ArchivePaths.rootOf(zip().path)

        assertTrue(router.createDirectory(root, "new").isFailure)
        val item = router.listFiles(root).getOrThrow().first()
        assertFalse(item.isWritable)
        assertTrue(router.delete(listOf(item)).isFailure)
        assertTrue(router.rename(item, "other").isFailure)
    }

    @Test
    fun `copying out of an archive is an ordinary transfer`() = runTest {
        val engine = TransferEngine(router, FileStreamCopier())
        val target = tmp.newFolder("out")
        val items = router.listFiles(ArchivePaths.rootOf(zip().path)).getOrThrow()

        val progress = engine.execute(TransferRequest(OperationType.COPY, items, target.path)) { _, _ -> error("no conflict expected") }.toList()

        assertEquals(progress.last().error, TransferStatus.COMPLETED, progress.last().status)
        assertEquals("hello", File(target, "readme.txt").readText())
        assertEquals("deep", File(target, "sub/deep/x.txt").readText())
    }

    @Test
    fun `extracting a 7z unpacks it into a new folder`() = runTest {
        val engine = TransferEngine(router, FileStreamCopier())
        val target = tmp.newFolder("out7")
        val item = local.stat(sevenZip().path).getOrThrow()

        val progress = engine.execute(TransferRequest(OperationType.EXTRACT, listOf(item), target.path)) { _, _ -> error("no conflict expected") }.toList()

        assertEquals(progress.last().error, TransferStatus.COMPLETED, progress.last().status)
        assertEquals("seven a", File(target, "pack/a.txt").readText())
        assertEquals("seven b", File(target, "pack/folder/b.txt").readText())
    }

    @Test
    fun `a file that is not an archive fails clearly`() = runTest {
        val notes = File(dir, "notes.txt").apply { writeText("x") }

        val result = router.listFiles(ArchivePaths.rootOf(notes.path))

        assertTrue(result.exceptionOrNull()!!.message!!.contains("notes.txt"))
    }
}
