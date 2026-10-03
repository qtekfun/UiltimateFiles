package com.qtekfun.fexplo.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileSystemRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = LocalFileSystemRepository(tmp.root, "Internal") { null }

    @Test
    fun `lists files with metadata`() = runTest {
        tmp.newFolder("sub")
        tmp.newFile("a.txt").writeText("hello")

        val items = repo().listFiles(tmp.root.path).getOrThrow().associateBy { it.name }

        assertTrue(items.getValue("sub").isDirectory)
        assertEquals(5L, items.getValue("a.txt").sizeBytes)
    }

    @Test
    fun `create rename and delete`() = runTest {
        val repo = repo()
        val dir = repo.createDirectory(tmp.root.path, "d").getOrThrow()
        val file = repo.createFile(dir.path, "f.txt", "text/plain").getOrThrow()
        val renamed = repo.rename(file, "g.txt").getOrThrow()

        assertEquals("g.txt", renamed.name)
        assertTrue(repo.rename(renamed, "../evil").isFailure)

        repo.delete(listOf(dir)).getOrThrow()
        assertFalse(tmp.root.resolve("d").exists())
    }

    @Test
    fun `parentOf stops at the volume root`() = runTest {
        val repo = repo()
        val child = tmp.newFolder("c")
        assertEquals(tmp.root.path, repo.parentOf(child.path))
        assertNull(repo.parentOf(tmp.root.path))
    }

    @Test
    fun `openOutput refuses to overwrite unless asked`() = runTest {
        val repo = repo()
        tmp.newFile("x.txt")
        assertTrue(repo.openOutput(tmp.root.path, "x.txt", "text/plain", overwrite = false).isFailure)
        repo.openOutput(tmp.root.path, "x.txt", "text/plain", overwrite = true).getOrThrow().close()
    }

    @Test
    fun `listing a missing directory fails`() = runTest {
        assertTrue(repo().listFiles(tmp.root.resolve("nope").path).isFailure)
    }

    @Test
    fun `stat reports posix permissions but listing does not`() = runTest {
        val file = tmp.newFile("p.txt")
        val repo = repo()

        val detailed = repo.stat(file.path).getOrThrow()
        val listed = repo.listFiles(tmp.root.path).getOrThrow().first { it.name == "p.txt" }

        assertTrue(Regex("[r-][w-][x-][r-][w-][x-][r-][w-][x-]").matches(detailed.permissions.orEmpty()))
        assertNull(listed.permissions)
    }
}
