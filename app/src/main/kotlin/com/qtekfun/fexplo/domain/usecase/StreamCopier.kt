package com.qtekfun.fexplo.domain.usecase

import java.io.InputStream
import java.io.OutputStream

/** Pipes [InputStream] into [OutputStream], reporting every chunk written. Must be cancellable. */
fun interface StreamCopier {
    /** Returns the number of bytes copied; [onBytes] receives the size of each chunk. */
    suspend fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit): Long
}
