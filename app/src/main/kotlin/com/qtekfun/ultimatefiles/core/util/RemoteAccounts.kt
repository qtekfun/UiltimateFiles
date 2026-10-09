package com.qtekfun.ultimatefiles.core.util

import com.qtekfun.ultimatefiles.core.model.FileItem

/** Tells which server account a path belongs to: `dav://<accountId>/...`, `sftp://<accountId>/...` or `smb://<accountId>/...`. */
object RemoteAccounts {
    private val SCHEMES = listOf("dav://", "sftp://", "smb://")
    private val VOLUME_PREFIXES = listOf("dav:", "sftp:", "smb:")

    /** The account id behind [path], or null for local, SAF and archive paths. */
    fun idOf(path: String): String? {
        val scheme = SCHEMES.firstOrNull { path.startsWith(it) } ?: return null
        return path.removePrefix(scheme).substringBefore('/').takeIf { it.isNotEmpty() }
    }

    fun isRemote(path: String): Boolean = idOf(path) != null

    /** Whether [path] is the top of an account (its root folder) rather than something inside it. */
    fun isRoot(path: String): Boolean {
        val scheme = SCHEMES.firstOrNull { path.startsWith(it) } ?: return false
        val rest = path.removePrefix(scheme)
        return rest.isNotEmpty() && rest.substringAfter('/', "").isEmpty()
    }

    /** The account ids of every remote path in [paths], without repeats. */
    fun idsIn(paths: Iterable<String>): Set<String> = paths.mapNotNull(::idOf).toSet()

    fun idsIn(items: List<FileItem>, targetDirectory: String): Set<String> = idsIn(items.map { it.path } + targetDirectory)

    /** The account id of a drawer volume (`dav:<id>`, `sftp:<id>`, `smb:<id>`), or null for any other volume. */
    fun idOfVolume(volumeId: String): String? =
        VOLUME_PREFIXES.firstOrNull { volumeId.startsWith(it) }?.let { volumeId.removePrefix(it).takeIf { id -> id.isNotEmpty() } }
}
