package com.qtekfun.ultimatefiles.domain.usecase

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Paths inside an archive: `archive://<url-encoded path of the archive>!/<path inside>`. The archive itself can be
 * anywhere the app can read (local, SAF, a server), so its path is whatever that backend uses, encoded.
 */
object ArchivePaths {
    const val SCHEME = "archive://"
    private const val SEPARATOR = "!/"

    fun isArchivePath(path: String) = path.startsWith(SCHEME)

    fun rootOf(archivePath: String) = SCHEME + URLEncoder.encode(archivePath, "UTF-8") + SEPARATOR

    fun pathOf(archivePath: String, inner: String) = rootOf(archivePath) + inner.trim('/')

    /** Splits into the archive's own path and the path inside it (no edge slashes, empty for the archive root). */
    fun split(path: String): Pair<String, String> {
        require(isArchivePath(path)) { "Not an archive path: $path" }
        val rest = path.removePrefix(SCHEME)
        val cut = rest.indexOf(SEPARATOR)
        require(cut >= 0) { "Not an archive path: $path" }
        return URLDecoder.decode(rest.substring(0, cut), "UTF-8") to rest.substring(cut + SEPARATOR.length).trim('/')
    }
}
