package com.qtekfun.fexplo.data.io

import com.qtekfun.fexplo.domain.usecase.StreamCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** Copies through a fixed 64 KB buffer on `Dispatchers.IO`, checking for cancellation between chunks. */
class FileStreamCopier(private val bufferSize: Int = DEFAULT_BUFFER_SIZE) : StreamCopier {

    override suspend fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit): Long =
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            var total = 0L
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
                onBytes(read.toLong())
            }
            output.flush()
            total
        }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
