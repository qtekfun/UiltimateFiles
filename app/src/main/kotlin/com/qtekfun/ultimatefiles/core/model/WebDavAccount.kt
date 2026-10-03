package com.qtekfun.ultimatefiles.core.model

/**
 * A WebDAV server the user connected (Nextcloud, ownCloud or any other).
 * [baseUrl] is the DAV root of the user's files, without trailing slash, e.g.
 * `https://cloud.example.com/remote.php/dav/files/alice`. The password lives in the encrypted store.
 * A pinned certificate replaces the system trust store for this server only: exactly that certificate is accepted.
 */
data class WebDavAccount(
    val id: String,
    val label: String,
    val baseUrl: String,
    val username: String,
    /** SHA-256 (lower-case hex) of the server certificate the user chose to trust, for servers with a self-signed one. */
    val pinnedCertSha256: String? = null,
    /** The user accepted an unencrypted `http://` address for this server. */
    val allowInsecureHttp: Boolean = false,
)
