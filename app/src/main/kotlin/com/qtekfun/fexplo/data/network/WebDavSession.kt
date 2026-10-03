package com.qtekfun.fexplo.data.network

import okhttp3.Credentials
import okhttp3.HttpUrl
import java.net.URI

/** Where and as whom to talk to one WebDAV account. Paths are relative to [baseUrl], decoded, without edge slashes. */
class WebDavSession(val baseUrl: HttpUrl, username: String, password: String) {
    val authorization: String = Credentials.basic(username, password)

    /** Nextcloud/ownCloud expose chunked uploads next to the files root; other servers do not. */
    val isNextcloud: Boolean = baseUrl.pathSegments.windowed(2).any { it == listOf("dav", "files") }

    fun urlFor(relativePath: String): HttpUrl = baseUrl.newBuilder().apply {
        relativePath.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
    }.build()

    /** `…/remote.php/dav/uploads/<user>` for the same user, or null when this is not a Nextcloud-style URL. */
    fun uploadsUrl(): HttpUrl? {
        val segments = baseUrl.pathSegments
        val index = segments.windowed(2).indexOfFirst { it == listOf("dav", "files") }
        if (index < 0) return null
        return baseUrl.newBuilder().apply {
            // pathSegments of a URL like https://h/remote.php/dav/files/u => [remote.php, dav, files, u]
            setPathSegment(index + 1, "uploads")
        }.build()
    }

    /** Converts a server `href` (percent-encoded path or absolute URL) to a path relative to [baseUrl]. */
    fun relativeTo(href: String): String {
        val path = URI(href).path.trim('/') // URI.path is already percent-decoded
        val base = baseUrl.pathSegments.filter { it.isNotEmpty() }.joinToString("/") // so are pathSegments
        return when {
            path == base -> ""
            path.startsWith("$base/") -> path.removePrefix("$base/")
            else -> path
        }
    }
}
