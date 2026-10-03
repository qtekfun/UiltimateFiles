package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.data.network.DavEntry
import com.qtekfun.ultimatefiles.data.network.UploadResumeStore
import com.qtekfun.ultimatefiles.data.network.WebDavClient
import com.qtekfun.ultimatefiles.data.network.WebDavSession
import com.qtekfun.ultimatefiles.data.network.WebDavUploadStream
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * [FileSystemRepository] over WebDAV for every connected account. Paths look like
 * `dav://<accountId>/<folder>/<file>` and the account root is `dav://<accountId>/`.
 */
class WebDavFileSystemRepository(
    private val accounts: AccountRepository,
    private val client: WebDavClient = WebDavClient(),
    private val uploadChunkSize: Int = WebDavUploadStream.DEFAULT_CHUNK_SIZE,
    private val resumeStore: UploadResumeStore? = null,
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    override suspend fun volumes(): List<StorageVolume> = accounts.accounts.first().filter { it.protocol == AccountProtocol.WEBDAV }.map { account ->
        StorageVolume(
            id = "dav:${account.id}",
            label = account.label,
            rootPath = rootOf(account.id),
            kind = StorageKind.NETWORK,
            isEjectable = false,
        )
    }

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val (account, session, path, client) = resolve(uriOrPath)
        client.propfind(session, path, depth = 1)
            .filter { it.path != path } // the first entry is the folder itself
            .map { it.toItem(account.id) }
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val (account, session, path, client) = resolve(uriOrPath)
        client.propfind(session, path, depth = 0).firstOrNull()?.toItem(account.id)
            ?: throw IOException("Not found: $uriOrPath")
    }

    override suspend fun parentOf(uriOrPath: String): String? {
        val (accountId, path) = split(uriOrPath)
        if (path.isEmpty()) return null
        return pathOf(accountId, path.substringBeforeLast('/', ""))
    }

    override suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, session, parent, client) = resolve(parentUriOrPath)
        val path = join(parent, name)
        client.mkcol(session, path)
        FileItem(pathOf(account.id, path), name, true, 0L, System.currentTimeMillis(), null)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, session, parent, client) = resolve(parentUriOrPath)
        val path = join(parent, name)
        client.putBytes(session, path, ByteArray(0), 0, mimeType, overwrite = false)
        FileItem(pathOf(account.id, path), name, false, 0L, System.currentTimeMillis(), mimeType)
    }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            val (_, session, path, client) = resolve(item.path)
            client.delete(session, path)
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val (account, session, path, client) = resolve(item.path)
        val target = join(path.substringBeforeLast('/', ""), newName)
        client.move(session, path, target, overwrite = false)
        item.copy(path = pathOf(account.id, target), name = newName)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        val (_, session, path, client) = resolve(item.path)
        client.open(session, path)
    }

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = ioResult {
        requireValidName(name)
        val (account, session, parent, client) = resolve(parentUriOrPath)
        val path = join(parent, name)
        if (!overwrite && client.exists(session, path)) throw IOException("Already exists: $name")
        // Same account and destination (the engine's temporary name is stable) means the same resumable upload.
        val key = MessageDigest.getInstance("SHA-256").digest("${account.id}|$path".toByteArray()).joinToString("") { "%02x".format(it) }
        WebDavUploadStream(client, session, path, mimeType, overwrite, uploadChunkSize, resumeStore, key)
    }

    // --- helpers --------------------------------------------------------------------------------

    private val clients = java.util.concurrent.ConcurrentHashMap<String, WebDavClient>()

    /** The client for one account: with its pinned certificate when it has one. */
    private fun clientFor(account: WebDavAccount): WebDavClient =
        clients.getOrPut("${account.id}|${account.pinnedCertSha256}") { client.pinnedTo(account.pinnedCertSha256) }

    private data class Target(val account: WebDavAccount, val session: WebDavSession, val path: String, val client: WebDavClient)

    private suspend fun resolve(uriOrPath: String): Target {
        val (accountId, path) = split(uriOrPath)
        val account = accounts.accounts.first().firstOrNull { it.id == accountId && it.protocol == AccountProtocol.WEBDAV }
            ?: throw IOException("Unknown account for $uriOrPath")
        val password = accounts.passwordOf(accountId) ?: throw IOException("No stored password for ${account.label}")
        val baseUrl = account.baseUrl.toHttpUrl()
        if (!baseUrl.isHttps && !account.allowInsecureHttp) {
            throw IOException("${account.label} uses an unencrypted address and was not allowed to")
        }
        return Target(account, WebDavSession(baseUrl, account.username, password), path, clientFor(account))
    }

    private fun DavEntry.toItem(accountId: String) = FileItem(
        path = pathOf(accountId, path),
        name = name,
        isDirectory = isDirectory,
        sizeBytes = if (isDirectory) 0L else sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        mimeType = if (isDirectory) null else (contentType ?: mimeOf(name)),
        isHidden = name.startsWith("."),
    )

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    companion object {
        const val SCHEME = "dav://"

        fun rootOf(accountId: String) = "$SCHEME$accountId/"

        fun pathOf(accountId: String, relative: String) = "$SCHEME$accountId/$relative"

        /** Splits `dav://<account>/<relative>` into the account id and the relative path (no edge slashes). */
        fun split(uriOrPath: String): Pair<String, String> {
            require(uriOrPath.startsWith(SCHEME)) { "Not a WebDAV path: $uriOrPath" }
            val rest = uriOrPath.removePrefix(SCHEME)
            val accountId = rest.substringBefore('/')
            return accountId to rest.substringAfter('/', "").trim('/')
        }
    }
}
