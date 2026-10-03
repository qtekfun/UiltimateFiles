package com.qtekfun.fexplo.domain.usecase

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import com.qtekfun.fexplo.core.model.StorageKind
import com.qtekfun.fexplo.core.model.StorageVolume
import com.qtekfun.fexplo.core.model.TransferRequest
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.data.io.FileStreamCopier
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.time.Duration.Companion.minutes

/**
 * Pushes an 8 GiB "video" through the real engine and copier. The data is generated on the fly and
 * the destination only checksums it, so the test needs no disk but still crosses every 2 GiB / 4 GiB
 * boundary where Int counters, FAT limits and progress maths could go wrong.
 */
class HugeFileTransferTest {
    private val size = 8L * 1024 * 1024 * 1024

    /** Deterministic pseudo-footage: a 1 MiB block whose first 8 bytes carry the block counter. */
    private class FootageStream(private val length: Long) : InputStream() {
        private val block = ByteArray(BLOCK) { (it * 31 + 7).toByte() }
        private var position = 0L

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (position >= length) return -1
            val count = minOf(len.toLong(), length - position).toInt()
            var done = 0
            while (done < count) {
                val inBlock = (position % BLOCK).toInt()
                if (inBlock == 0) {
                    val counter = position / BLOCK
                    for (i in 0 until 8) block[i] = (counter shr (8 * i)).toByte()
                }
                val chunk = minOf(count - done, BLOCK - inBlock)
                System.arraycopy(block, inBlock, buffer, off + done, chunk)
                done += chunk
                position += chunk
            }
            return count
        }

        companion object {
            const val BLOCK = 1024 * 1024
        }
    }

    private class ChecksumSink : OutputStream() {
        val crc = CRC32()
        var bytes = 0L
        override fun write(b: Int) = throw UnsupportedOperationException()
        override fun write(b: ByteArray, off: Int, len: Int) {
            crc.update(b, off, len)
            bytes += len
        }
    }

    private class FakeRepository(private val file: FileItem, val sink: ChecksumSink) : FileSystemRepository {
        val renamed = mutableListOf<String>()

        override suspend fun volumes() = listOf(StorageVolume("v", "V", "/", StorageKind.INTERNAL, false))
        override suspend fun listFiles(uriOrPath: String) = Result.success(
            when (uriOrPath) {
                "/src" -> listOf(file)
                "/dst" -> sinkEntries
                else -> emptyList()
            },
        )
        private val sinkEntries = mutableListOf<FileItem>()
        override suspend fun stat(uriOrPath: String) = Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun parentOf(uriOrPath: String): String? = null
        override suspend fun createDirectory(parentUriOrPath: String, name: String) =
            Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String) =
            Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun delete(items: List<FileItem>): Result<Unit> = Result.success(Unit)
        override suspend fun rename(item: FileItem, newName: String): Result<FileItem> {
            renamed += newName
            val renamedItem = item.copy(path = "/dst/$newName", name = newName)
            sinkEntries.replaceAll { if (it.path == item.path) renamedItem else it }
            return Result.success(renamedItem)
        }
        override suspend fun openInput(item: FileItem): Result<InputStream> = Result.success(FootageStream(size(item)))
        override suspend fun openOutput(
            parentUriOrPath: String,
            name: String,
            mimeType: String,
            overwrite: Boolean,
        ): Result<OutputStream> {
            sinkEntries += FileItem("$parentUriOrPath/$name", name, false, 0, 0, mimeType)
            return Result.success(sink)
        }
        private fun size(item: FileItem) = file.sizeBytes
    }

    @Test
    fun `copies an 8 GiB file with exact byte counts and an intact checksum`() = runTest(timeout = 10.minutes) {
        val file = FileItem("/src/footage.mov", "footage.mov", false, size, 0, "video/quicktime")
        val sink = ChecksumSink()
        val repository = FakeRepository(file, sink)
        val engine = TransferEngine(repository, FileStreamCopier())

        val progress = engine.execute(TransferRequest(OperationType.COPY, listOf(file), "/dst")) { _, _ ->
            error("no conflict expected")
        }.toList()

        val expected = CRC32()
        FootageStream(size).let { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = stream.read(buffer, 0, buffer.size)
                if (read < 0) break
                expected.update(buffer, 0, read)
            }
        }

        val last = progress.last()
        assertEquals(TransferStatus.COMPLETED, last.status)
        assertEquals(size, sink.bytes)
        assertEquals(size, last.processedBytes)
        assertEquals(size, last.totalBytes)
        assertEquals(1f, last.fraction)
        assertEquals(expected.value, sink.crc.value)
        // 8 GiB is a "big" file: it went through the temporary name and was renamed at the end.
        assertEquals(listOf("footage.mov"), repository.renamed)
        // Progress never goes backwards and the speed estimate is live.
        assertTrue(progress.zipWithNext().all { (a, b) -> b.processedBytes >= a.processedBytes })
        assertNotNull(progress.firstOrNull { it.bytesPerSecond > 0 })
    }
}
