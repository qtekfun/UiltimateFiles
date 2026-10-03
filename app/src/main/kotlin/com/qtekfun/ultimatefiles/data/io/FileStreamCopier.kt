package com.qtekfun.ultimatefiles.data.io

import com.qtekfun.ultimatefiles.domain.transfer.PauseGate
import com.qtekfun.ultimatefiles.domain.usecase.StreamCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies through a 64 KB buffer (1 MB for big files, where per-call overhead shows) on `Dispatchers.IO`,
 * checking for cancellation between chunks.
 */
class FileStreamCopier(private val bufferSize: Int = DEFAULT_BUFFER_SIZE) : StreamCopier {

    override suspend fun copy(
        input: InputStream,
        output: OutputStream,
        sizeHint: Long,
        digest: MessageDigest?,
        gate: PauseGate?,
        onBytes: (Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val buffer = ByteArray(if (sizeHint >= BIG_FILE_HINT) BIG_BUFFER_SIZE else bufferSize)
        var total = 0L
        while (true) {
            ensureActive()
            gate?.awaitResumed()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            digest?.update(buffer, 0, read)
            total += read
            onBytes(read.toLong())
        }
        output.flush()
        total
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        const val BIG_BUFFER_SIZE = 1024 * 1024
        private const val BIG_FILE_HINT = 64L * 1024 * 1024
    }
}
