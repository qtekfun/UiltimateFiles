package com.qtekfun.ultimatefiles.data.network

import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Turns the byte stream the transfer engine produces into WebDAV uploads. Files that fit in one
 * [chunkSize] go out as a single PUT; bigger ones use Nextcloud's chunked upload (MKCOL, numbered PUTs,
 * then a MOVE that assembles them on the server), so a dropped connection costs one chunk, not the file.
 * Only one chunk is ever held in memory.
 *
 * With a [resume] store (and a stable [resumeKey] for the destination) the upload survives the app dying: the chunk
 * hashes are persisted as they are sent, and a later upload of the same destination skips every leading chunk whose
 * hash matches the data now being read and that the server still holds. Nothing is assumed about the source file.
 */
internal class WebDavUploadStream(
    private val client: WebDavClient,
    private val session: WebDavSession,
    private val path: String,
    private val mime: String,
    private val overwrite: Boolean,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val resume: UploadResumeStore? = null,
    private val resumeKey: String? = null,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : OutputStream() {
    private var buffer = ByteArray(INITIAL_BUFFER.coerceAtMost(chunkSize))
    private var count = 0
    private var total = 0L
    private var chunkIndex = 0
    private var uploadId: String? = null
    private var closed = false
    private val hashes = mutableListOf<String>()
    private var serverChunks: Map<Int, Long>? = null
    private var reusing = false

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
        if (uploadId == null) beginChunkedUpload()
        val id = uploadId!!
        chunkIndex++
        val hash = sha256(buffer, count)
        val onServer = serverChunks?.get(chunkIndex)
        if (reusing && hashes.getOrNull(chunkIndex - 1) == hash && onServer == count.toLong()) {
            // Already uploaded by an earlier run and identical to what is being read now.
        } else {
            reusing = false
            while (hashes.size > chunkIndex - 1) hashes.removeAt(hashes.size - 1)
            client.putChunk(session, id, chunkIndex, buffer, count, path)
            hashes.add(hash)
        }
        remember()
        count = 0
    }

    /** Continues the upload an earlier run left behind when the server still has it, else starts a new one. */
    private fun beginChunkedUpload() {
        val saved = if (resume != null && resumeKey != null) resume.load(resumeKey) else null
        if (saved != null && saved.chunkSize == chunkSize) {
            val chunks = client.listChunks(session, saved.uploadId)
            if (chunks != null) {
                uploadId = saved.uploadId
                serverChunks = chunks
                hashes.addAll(saved.chunkHashes)
                reusing = true
                return
            }
            resume?.clear(resumeKey!!)
        }
        val id = UUID.randomUUID().toString()
        client.startChunkedUpload(session, id, path)
        uploadId = id
    }

    private fun remember() {
        val key = resumeKey ?: return
        val id = uploadId ?: return
        resume?.save(key, UploadRecord(id, chunkSize, hashes.toList(), clockMillis()))
    }

    /** Chunks of an earlier, longer attempt must not end up in the assembled file. */
    private fun dropStaleChunks(id: String) {
        serverChunks?.keys?.filter { it > chunkIndex }?.forEach { client.deleteChunk(session, id, it) }
    }

    private fun sha256(bytes: ByteArray, length: Int): String =
        MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, length) }.digest().joinToString("") { "%02x".format(it) }

    override fun close() {
        if (closed) return
        closed = true
        val id = uploadId
        try {
            if (id == null) {
                client.putBytes(session, path, buffer, count, mime, overwrite)
            } else {
                if (count > 0) sendChunk()
                dropStaleChunks(id)
                client.finishChunkedUpload(session, id, path, total, overwrite)
                resumeKey?.let { resume?.clear(it) }
            }
        } catch (e: Exception) {
            id?.let { client.abortChunkedUpload(session, it) }
            resumeKey?.let { resume?.clear(it) }
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
