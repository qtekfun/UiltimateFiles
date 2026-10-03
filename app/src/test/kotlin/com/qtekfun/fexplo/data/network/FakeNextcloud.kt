package com.qtekfun.fexplo.data.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64

/**
 * A tiny in-memory Nextcloud: the `files` and `uploads` DAV endpoints with Basic auth, PROPFIND, MKCOL, PUT,
 * GET (with `Range`), DELETE, MOVE and the v2 chunked-upload assembly. Enough to exercise the real client.
 */
internal class FakeNextcloud(private val user: String = "alice", private val password: String = "secret") : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val filesPrefix = "/remote.php/dav/files/$user"
    private val uploadsPrefix = "/remote.php/dav/uploads/$user"

    val files = linkedMapOf<String, ByteArray>()
    val dirs = linkedSetOf("")
    val log = mutableListOf<String>()

    /** One-shot fault: the next full GET sends this many bytes and then drops the connection. */
    @Volatile var dropNextGetAfterBytes: Int? = null

    private class Upload(val destination: String) {
        val chunks = sortedMapOf<String, ByteArray>()
    }
    private val uploads = mutableMapOf<String, Upload>()

    /** Login Flow v2: the poll answers 404 until the test calls [approveLogin]. */
    @Volatile var loginApproved = false
    val pollCount = java.util.concurrent.atomic.AtomicInteger()
    val rootUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun approveLogin() { loginApproved = true }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}$filesPrefix"

    init {
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (e: Exception) {
                runCatching { exchange.sendResponseHeaders(500, -1) }
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    override fun close() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        synchronized(log) { log += "${ex.requestMethod} ${path.removePrefix("/remote.php/dav")}" }
        if (path == "/index.php/login/v2" && ex.requestMethod == "POST") {
            return json(ex, """{"poll":{"token":"tok123","endpoint":"$rootUrl/index.php/login/v2/poll"},"login":"$rootUrl/index.php/login/v2/flow/tok123"}""")
        }
        if (path == "/index.php/login/v2/poll" && ex.requestMethod == "POST") {
            pollCount.incrementAndGet()
            val token = ex.requestBody.readBytes().decodeToString()
            return if (token == "token=tok123" && loginApproved) {
                json(ex, """{"server":"$rootUrl","loginName":"$user","appPassword":"$password"}""")
            } else {
                respond(ex, 404)
            }
        }
        val expected = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        if (ex.requestHeaders.getFirst("Authorization") != expected) return respond(ex, 401)
        when {
            path.startsWith(filesPrefix) -> handleFiles(ex, path.removePrefix(filesPrefix).trim('/'))
            path.startsWith(uploadsPrefix) -> handleUploads(ex, path.removePrefix(uploadsPrefix).trim('/'))
            else -> respond(ex, 404)
        }
    }

    private fun handleFiles(ex: HttpExchange, rel: String) {
        when (ex.requestMethod) {
            "PROPFIND" -> propfind(ex, rel)
            "MKCOL" -> when {
                rel in dirs || rel in files -> respond(ex, 405)
                parent(rel) !in dirs -> respond(ex, 409)
                else -> { dirs += rel; respond(ex, 201) }
            }
            "PUT" -> when {
                parent(rel) !in dirs -> respond(ex, 409)
                ex.requestHeaders.getFirst("If-None-Match") == "*" && (rel in files || rel in dirs) -> respond(ex, 412)
                else -> { files[rel] = ex.requestBody.readBytes(); respond(ex, 201) }
            }
            "GET" -> get(ex, rel)
            "DELETE" -> {
                val found = rel in files || rel in dirs
                files.keys.removeAll { it == rel || it.startsWith("$rel/") }
                dirs.removeAll { it == rel || it.startsWith("$rel/") }
                respond(ex, if (found) 204 else 404)
            }
            "MOVE" -> {
                val destination = relativeFiles(ex.requestHeaders.getFirst("Destination"))
                val overwrite = ex.requestHeaders.getFirst("Overwrite") != "F"
                val data = files[rel]
                when {
                    data == null -> respond(ex, 404)
                    destination in files && !overwrite -> respond(ex, 412)
                    else -> { files.remove(rel); files[destination] = data; respond(ex, 201) }
                }
            }
            else -> respond(ex, 405)
        }
    }

    private fun handleUploads(ex: HttpExchange, rel: String) {
        val parts = rel.split('/')
        val uploadId = parts[0]
        when (ex.requestMethod) {
            "MKCOL" -> {
                uploads[uploadId] = Upload(relativeFiles(ex.requestHeaders.getFirst("Destination")))
                respond(ex, 201)
            }
            "PUT" -> {
                val upload = uploads[uploadId] ?: return respond(ex, 404)
                upload.chunks[parts[1]] = ex.requestBody.readBytes()
                respond(ex, 201)
            }
            "MOVE" -> {
                val upload = uploads[uploadId] ?: return respond(ex, 404)
                val assembled = upload.chunks.values.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
                val declared = ex.requestHeaders.getFirst("OC-Total-Length")?.toLong()
                if (declared != null && declared != assembled.size.toLong()) return respond(ex, 400)
                files[relativeFiles(ex.requestHeaders.getFirst("Destination"))] = assembled
                uploads.remove(uploadId)
                respond(ex, 201)
            }
            "DELETE" -> { uploads.remove(uploadId); respond(ex, 204) }
            else -> respond(ex, 405)
        }
    }

    private fun get(ex: HttpExchange, rel: String) {
        val data = files[rel] ?: return respond(ex, 404)
        val range = ex.requestHeaders.getFirst("Range")
        if (range != null) {
            val from = range.removePrefix("bytes=").substringBefore('-').toInt()
            val part = data.copyOfRange(from, data.size)
            ex.responseHeaders.add("Content-Range", "bytes $from-${data.size - 1}/${data.size}")
            ex.sendResponseHeaders(206, part.size.toLong())
            ex.responseBody.write(part)
            return
        }
        val drop = dropNextGetAfterBytes
        ex.sendResponseHeaders(200, data.size.toLong())
        if (drop != null) {
            dropNextGetAfterBytes = null
            ex.responseBody.write(data, 0, drop)
            ex.responseBody.flush()
            ex.close() // fewer bytes than announced: the client sees an unexpected end of stream
            return
        }
        ex.responseBody.write(data)
    }

    private fun propfind(ex: HttpExchange, rel: String) {
        val isDir = rel in dirs
        if (!isDir && rel !in files) return respond(ex, 404)
        val xml = StringBuilder("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
        fun entry(path: String, dir: Boolean) {
            val href = URI(null, null, "$filesPrefix/$path".trimEnd('/') + if (dir) "/" else "", null).rawPath
            xml.append("<d:response><d:href>$href</d:href><d:propstat><d:prop>")
            if (dir) {
                xml.append("<d:resourcetype><d:collection/></d:resourcetype>")
            } else {
                xml.append("<d:resourcetype/><d:getcontentlength>${files.getValue(path).size}</d:getcontentlength>")
                xml.append("<d:getcontenttype>application/octet-stream</d:getcontenttype>")
            }
            xml.append("<d:getlastmodified>Sat, 03 Oct 2026 10:00:00 GMT</d:getlastmodified>")
            xml.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>")
        }
        entry(rel, isDir)
        if (isDir && ex.requestHeaders.getFirst("Depth") == "1") {
            (dirs.map { it to true } + files.keys.map { it to false })
                .filter { (path, _) -> path.isNotEmpty() && parent(path) == rel }
                .forEach { (path, dir) -> entry(path, dir) }
        }
        xml.append("</d:multistatus>")
        val bytes = xml.toString().toByteArray()
        ex.responseHeaders.add("Content-Type", "application/xml")
        ex.sendResponseHeaders(207, bytes.size.toLong())
        ex.responseBody.write(bytes)
    }

    private fun parent(path: String) = path.substringBeforeLast('/', "")

    private fun relativeFiles(destination: String?): String =
        URI(destination!!).path.removePrefix(filesPrefix).trim('/')

    private fun json(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.write(bytes)
    }

    private fun respond(ex: HttpExchange, code: Int) = ex.sendResponseHeaders(code, -1)
}
