package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowsingUseCasesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = LocalFileSystemRepository(tmp.root, "Internal") { null }

    @Test
    fun `breadcrumb goes from the volume label down to the folder`() = runTest {
        val leaf = tmp.newFolder("a", "b")

        val segments = BuildBreadcrumbUseCase(repo())(leaf.path)

        assertEquals(listOf("Internal", "a", "b"), segments.map { it.label })
        assertEquals(tmp.root.path, segments.first().path)
        assertEquals(leaf.path, segments.last().path)
    }

    @Test
    fun `breadcrumb of the root is just the volume`() = runTest {
        assertEquals(listOf("Internal"), BuildBreadcrumbUseCase(repo())(tmp.root.path).map { it.label })
    }

    @Test
    fun `hashes match known digests`() = runTest {
        tmp.newFile("abc.txt").writeText("abc")
        val repo = repo()
        val item = repo.stat(tmp.root.resolve("abc.txt").path).getOrThrow()

        val hashes = HashCalcUseCase(repo)(item).getOrThrow()

        assertEquals("900150983cd24fb0d6963f7d28e17f72", hashes.md5)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashes.sha256)
    }

    @Test
    fun `hash of a missing file fails`() = runTest {
        tmp.newFile("gone.txt")
        val repo = repo()
        val item = repo.stat(tmp.root.resolve("gone.txt").path).getOrThrow()
        tmp.root.resolve("gone.txt").delete()

        assertTrue(HashCalcUseCase(repo)(item).isFailure)
    }
}
