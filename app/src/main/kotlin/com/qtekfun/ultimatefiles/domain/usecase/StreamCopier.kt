package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.domain.transfer.PauseGate
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Pipes [InputStream] into [OutputStream], reporting every chunk written. Must be cancellable. */
interface StreamCopier {
    /**
     * Returns the number of bytes copied. [onBytes] receives the size of each chunk, [digest] (when given)
     * is fed with everything copied, and [sizeHint] (size of the source, 0 if unknown) lets the
     * implementation use a bigger buffer for big files. While [gate] is paused the copy waits between chunks.
     */
    suspend fun copy(
        input: InputStream,
        output: OutputStream,
        sizeHint: Long = 0L,
        digest: MessageDigest? = null,
        gate: PauseGate? = null,
        onBytes: (Long) -> Unit,
    ): Long
}
