package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.data.system.RemovableStorage
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class RemovableVolumesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun usb(root: File) =
        StorageVolume("${RemovableStorage.ID_PREFIX}1234-ABCD", "USB drive", root.path, StorageKind.USB_OTG, isEjectable = true)

    @Test
    fun `a drive reachable by path is listed after the internal storage and is a root of its own`() = runTest {
        val internal = tmp.newFolder("internal")
        val drive = tmp.newFolder("drive")
        File(drive, "sub").mkdirs()
        val repo = LocalFileSystemRepository(internal, "Internal", extraVolumes = { listOf(usb(drive)) })

        assertEquals(listOf("Internal", "USB drive"), repo.volumes().map { it.label })
        assertNull("the drive is a root: going up does not leave it", repo.parentOf(drive.path))
        assertEquals(drive.path, repo.parentOf(File(drive, "sub").path))
        assertNull(repo.parentOf(internal.path))
    }

    @Test
    fun `an unplugged drive disappears from the list`() = runTest {
        val drive = tmp.newFolder("drive")
        var present = true
        val repo = LocalFileSystemRepository(tmp.newFolder("internal"), "Internal", extraVolumes = { if (present) listOf(usb(drive)) else emptyList() })

        assertEquals(2, repo.volumes().size)
        present = false
        assertEquals(1, repo.volumes().size)
    }

    @Test
    fun `a granted folder of a drive already shown by path is recognised`() {
        val id = "${RemovableStorage.ID_PREFIX}1234-ABCD"
        assertTrue(RemovableStorage.isSameVolume("content://com.android.externalstorage.documents/tree/1234-ABCD%3A", id))
        assertTrue(RemovableStorage.isSameVolume("content://com.android.externalstorage.documents/tree/1234-ABCD:", id))
        assertFalse(RemovableStorage.isSameVolume("content://com.android.externalstorage.documents/tree/9999-FFFF%3A", id))
        assertFalse(RemovableStorage.isSameVolume("content://com.android.externalstorage.documents/tree/primary%3ADCIM", id))
    }

    private class VolumesOnly(private val volumes: List<StorageVolume>) : FileSystemRepository {
        override suspend fun volumes() = volumes
        override suspend fun listFiles(uriOrPath: String) = TODO()
        override suspend fun stat(uriOrPath: String) = TODO()
        override suspend fun parentOf(uriOrPath: String) = TODO()
        override suspend fun createDirectory(parentUriOrPath: String, name: String) = TODO()
        override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String) = TODO()
        override suspend fun delete(items: List<FileItem>) = TODO()
        override suspend fun rename(item: FileItem, newName: String) = TODO()
        override suspend fun openInput(item: FileItem): Result<InputStream> = TODO()
        override suspend fun openOutput(parentUriOrPath: String, name: String, mimeType: String, overwrite: Boolean): Result<OutputStream> = TODO()
    }

    @Test
    fun `the drawer lists a drive once even if a folder was granted for it before`() = runTest {
        val drive = tmp.newFolder("drive")
        val local = LocalFileSystemRepository(tmp.newFolder("internal"), "Internal", extraVolumes = { listOf(usb(drive)) })
        val sameDriveGranted = StorageVolume("content://x/tree/1234-ABCD%3A", "1234-ABCD", "content://x/tree/1234-ABCD%3A/document/1234-ABCD%3A", StorageKind.USB_OTG, true)
        val otherFolder = StorageVolume("content://x/tree/primary%3ADCIM", "DCIM", "content://x/tree/primary%3ADCIM/document/primary%3ADCIM", StorageKind.INTERNAL, false)
        val none = VolumesOnly(emptyList())
        val routing = RoutingFileSystemRepository(local, VolumesOnly(listOf(sameDriveGranted, otherFolder)), none, none, none)

        assertEquals(listOf("Internal", "USB drive", "DCIM"), routing.volumes().map { it.label })
    }
}
