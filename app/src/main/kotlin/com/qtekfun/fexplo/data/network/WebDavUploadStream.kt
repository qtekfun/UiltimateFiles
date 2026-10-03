package com.qtekfun.fexplo.data.network

import java.io.OutputStream
import java.util.UUID

/**
 * Turns the byte stream the transfer engine produces into WebDAV uploads. Files that fit in one
 * [chunkSize] go out as a single PUT; bigger ones use Nextcloud's chunked upload (MKCOL, numbered PUTs,
 * then a MOVE that assembles them on the server), so a dropped connection costs one chunk, not the file.
 * Only one chunk is ever held in memory.
 */
internal class WebDavUploadStream(
    private val client: WebDavClient,
    private val session: WebDavSession,
    private val path: String,
    private val mime: String,
    private val overwrite: Boolean,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) : OutputStream() {
    private var buffer = ByteArray(INITIAL_BUFFER.coerceAtMost(chunkSize))
    private var count = 0
    private var total = 0L
    private var chunkIndex = 0
    private var uploadId: String? = null
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is only sent once more data arrives, so a file of exactly one chunk stays a single PUT.
            if (count == chunkSize) sendChunk()
            if (count == buffer.size) buffer = buffer.copyOf(minOf(chunkSize, buffer.size * 2))
            val n = minOf(remaining, buffer.size - count)
            System.arraycopy(b, offset, buffer, count, n)
            count += n
            offset += n
            remaining -= n
            total += n
        }
    }

    private fun sendChunk() {
        val id = uploadId ?: UUID.randomUUID().toString().also {
            uploadId = it
            client.startChunkedUpload(session, it, path)
        }
        chunkIndex++
        client.putChunk(session, id, chunkIndex, buffer, count, path)
        count = 0
    }

    override fun close() {
        if (closed) return
        closed = true
        val id = uploadId
        try {
            if (id == null) {
                client.putBytes(session, path, buffer, count, mime, overwrite)
            } else {
                if (count > 0) sendChunk()
                client.finishChunkedUpload(session, id, path, total, overwrite)
            }
        } catch (e: Exception) {
            id?.let { client.abortChunkedUpload(session, it) }
            throw e
        } finally {
            buffer = ByteArray(0)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024
        private const val INITIAL_BUFFER = 64 * 1024
    }
}
