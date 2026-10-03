package com.qtekfun.fexplo.data.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** A WebDAV answer that is not a success; [code] is the HTTP status. */
class WebDavException(val code: Int, message: String) : IOException(message)

/**
 * Blocking WebDAV client (call it from `Dispatchers.IO`). Every request is retried a few times on
 * network errors and 5xx answers, which is what makes multi-gigabyte transfers over mobile networks viable.
 */
class WebDavClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val retryDelayMillis: Long = 1_000,
) {
    fun propfind(session: WebDavSession, path: String, depth: Int): List<DavEntry> = retrying {
        val body = PROPFIND_BODY.toRequestBody(XML)
        val request = Request.Builder().url(session.urlFor(path))
            .method("PROPFIND", body)
            .header("Depth", depth.toString())
            .authorized(session).build()
        http.newCall(request).execute().use { response ->
            ensureSuccess(response, path)
            WebDavXml.parse(response.body!!.byteStream(), session)
        }
    }

    fun exists(session: WebDavSession, path: String): Boolean = try {
        propfind(session, path, 0).isNotEmpty()
    } catch (e: WebDavException) {
        if (e.code == 404) false else throw e
    }

    fun mkcol(session: WebDavSession, path: String) = send(
        Request.Builder().url(session.urlFor(path)).method("MKCOL", null).authorized(session).build(),
        path,
    )

    fun delete(session: WebDavSession, path: String) {
        try {
            send(Request.Builder().url(session.urlFor(path)).delete().authorized(session).build(), path)
        } catch (e: WebDavException) {
            if (e.code != 404) throw e // already gone is fine
        }
    }

    fun move(session: WebDavSession, from: String, to: String, overwrite: Boolean) = send(
        Request.Builder().url(session.urlFor(from)).method("MOVE", null)
            .header("Destination", session.urlFor(to).toString())
            .header("Overwrite", if (overwrite) "T" else "F")
            .authorized(session).build(),
        from,
    )

    /** Single-request upload for files that fit in one chunk. */
    fun putBytes(session: WebDavSession, path: String, bytes: ByteArray, length: Int, mime: String, overwrite: Boolean) {
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull(), 0, length)
        val builder = Request.Builder().url(session.urlFor(path)).put(body).authorized(session)
        if (!overwrite) builder.header("If-None-Match", "*")
        send(builder.build(), path)
    }

    /** Opens a download that transparently resumes with a `Range` request if the connection drops. */
    fun open(session: WebDavSession, path: String): InputStream = DownloadStream(session, path)

    // --- Nextcloud chunked upload (v2) -------------------------------------------------------

    fun startChunkedUpload(session: WebDavSession, uploadId: String, finalPath: String) {
        val uploads = requireNotNull(session.uploadsUrl()) { "Chunked upload needs a Nextcloud URL" }
        send(
            Request.Builder().url(uploads.newBuilder().addPathSegment(uploadId).build())
                .method("MKCOL", null)
                .header("Destination", session.urlFor(finalPath).toString())
                .authorized(session).build(),
            finalPath,
        )
    }

    fun putChunk(session: WebDavSession, uploadId: String, index: Int, bytes: ByteArray, length: Int, finalPath: String) {
        val uploads = requireNotNull(session.uploadsUrl())
        val url = uploads.newBuilder().addPathSegment(uploadId).addPathSegment(String.format(java.util.Locale.ROOT, "%05d", index)).build()
        val body = bytes.toRequestBody(OCTET, 0, length)
        send(
            Request.Builder().url(url).put(body)
                .header("Destination", session.urlFor(finalPath).toString())
                .authorized(session).build(),
            finalPath,
        )
    }

    fun finishChunkedUpload(session: WebDavSession, uploadId: String, finalPath: String, totalLength: Long, overwrite: Boolean) {
        val uploads = requireNotNull(session.uploadsUrl())
        send(
            Request.Builder().url(uploads.newBuilder().addPathSegment(uploadId).addPathSegment(".file").build())
                .method("MOVE", null)
                .header("Destination", session.urlFor(finalPath).toString())
                .header("OC-Total-Length", totalLength.toString())
                .header("Overwrite", if (overwrite) "T" else "F")
                .authorized(session).build(),
            finalPath,
        )
    }

    /** Best effort cleanup of an upload that will never be finished. */
    fun abortChunkedUpload(session: WebDavSession, uploadId: String) {
        val uploads = session.uploadsUrl() ?: return
        try {
            send(Request.Builder().url(uploads.newBuilder().addPathSegment(uploadId).build()).delete().authorized(session).build(), uploadId)
        } catch (ignored: IOException) {
        }
    }

    // --- plumbing -------------------------------------------------------------------------------

    private fun send(request: Request, path: String) {
        retrying { http.newCall(request).execute().use { ensureSuccess(it, path) } }
    }

    private fun ensureSuccess(response: Response, path: String) {
        if (response.isSuccessful) return
        val reason = when (response.code) {
            401 -> "Authentication failed"
            403 -> "Access denied"
            404 -> "Not found: $path"
            405 -> "Already exists: $path"
            409 -> "Parent folder missing: $path"
            412 -> "Already exists: $path"
            413, 507 -> "Not enough space on the server"
            423 -> "Locked: $path"
            else -> "Server answered ${response.code} ${response.message}"
        }
        throw WebDavException(response.code, reason)
    }

    private fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                val permanent = e is WebDavException && e.code < 500 && e.code != 408 && e.code != 429
                if (permanent || ++attempt >= MAX_ATTEMPTS) throw e
                Thread.sleep(retryDelayMillis * attempt)
            }
        }
    }

    private fun Request.Builder.authorized(session: WebDavSession) = header("Authorization", session.authorization)

    private inner class DownloadStream(private val session: WebDavSession, private val path: String) : InputStream() {
        private var response: Response? = null
        private var body: InputStream? = null
        private var position = 0L
        private var failures = 0

        private fun connect() {
            val builder = Request.Builder().url(session.urlFor(path)).get().authorized(session)
            if (position > 0) builder.header("Range", "bytes=$position-")
            val answer = http.newCall(builder.build()).execute()
            try {
                ensureSuccess(answer, path)
                if (position > 0 && answer.code != 206) throw IOException("The server does not support resuming downloads")
            } catch (e: IOException) {
                answer.close()
                throw e
            }
            response = answer
            body = answer.body!!.byteStream()
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            while (true) {
                try {
                    if (body == null) connect()
                    val read = body!!.read(buffer, off, len)
                    if (read > 0) position += read
                    return read
                } catch (e: IOException) {
                    closeQuietly()
                    val permanent = e is WebDavException && e.code < 500
                    if (permanent || ++failures > MAX_ATTEMPTS) throw e
                    Thread.sleep(retryDelayMillis * failures)
                }
            }
        }

        override fun close() = closeQuietly()

        private fun closeQuietly() {
            try {
                response?.close()
            } catch (ignored: Exception) {
            }
            response = null
            body = null
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private val XML = "application/xml".toMediaTypeOrNull()
        private val OCTET = "application/octet-stream".toMediaTypeOrNull()
        private const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/>""" +
                """<d:getlastmodified/><d:getcontenttype/></d:prop></d:propfind>"""

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
