package com.qtekfun.ultimatefiles.core.model

/**
 * A WebDAV server the user connected (Nextcloud, ownCloud or any other).
 * [baseUrl] is the DAV root of the user's files, without trailing slash, e.g.
 * `https://cloud.example.com/remote.php/dav/files/alice`. The password lives in the encrypted store.
 */
data class WebDavAccount(
    val id: String,
    val label: String,
    val baseUrl: String,
    val username: String,
)
