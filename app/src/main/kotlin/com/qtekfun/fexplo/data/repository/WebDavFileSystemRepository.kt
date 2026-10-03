package com.qtekfun.fexplo.data.repository

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.StorageKind
import com.qtekfun.fexplo.core.model.StorageVolume
import com.qtekfun.fexplo.core.model.WebDavAccount
import com.qtekfun.fexplo.core.util.MimeTypes
import com.qtekfun.fexplo.data.network.DavEntry
import com.qtekfun.fexplo.data.network.WebDavClient
import com.qtekfun.fexplo.data.network.WebDavSession
import com.qtekfun.fexplo.data.network.WebDavUploadStream
import com.qtekfun.fexplo.domain.repository.AccountRepository
import com.qtekfun.fexplo.domain.repository.FileSystemRepository
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [FileSystemRepository] over WebDAV for every connected account. Paths look like
 * `dav://<accountId>/<folder>/<file>` and the account root is `dav://<accountId>/`.
 */
class WebDavFileSystemRepository(
    private val accounts: AccountRepository,
    private val client: WebDavClient = WebDavClient(),
    private val uploadChunkSize: Int = WebDavUploadStream.DEFAULT_CHUNK_SIZE,
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    override suspend fun volumes(): List<StorageVolume> = accounts.accounts.first().map { account ->
        StorageVolume(
            id = "dav:${account.id}",
            label = account.label,
            rootPath = rootOf(account.id),
            kind = StorageKind.NETWORK,
            isEjectable = false,
        )
    }

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val (account, session, path) = resolve(uriOrPath)
        client.propfind(session, path, depth = 1)
            .filter { it.path != path } // the first entry is the folder itself
            .map { it.toItem(account.id) }
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val (account, session, path) = resolve(uriOrPath)
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
        val (account, session, parent) = resolve(parentUriOrPath)
        val path = join(parent, name)
        client.mkcol(session, path)
        FileItem(pathOf(account.id, path), name, true, 0L, System.currentTimeMillis(), null)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, session, parent) = resolve(parentUriOrPath)
        val path = join(parent, name)
        client.putBytes(session, path, ByteArray(0), 0, mimeType, overwrite = false)
        FileItem(pathOf(account.id, path), name, false, 0L, System.currentTimeMillis(), mimeType)
    }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            val (_, session, path) = resolve(item.path)
            client.delete(session, path)
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val (account, session, path) = resolve(item.path)
        val target = join(path.substringBeforeLast('/', ""), newName)
        client.move(session, path, target, overwrite = false)
        item.copy(path = pathOf(account.id, target), name = newName)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        val (_, session, path) = resolve(item.path)
        client.open(session, path)
    }

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = ioResult {
        requireValidName(name)
        val (_, session, parent) = resolve(parentUriOrPath)
        val path = join(parent, name)
        if (!overwrite && client.exists(session, path)) throw IOException("Already exists: $name")
        WebDavUploadStream(client, session, path, mimeType, overwrite, uploadChunkSize)
    }

    // --- helpers --------------------------------------------------------------------------------

    private data class Target(val account: WebDavAccount, val session: WebDavSession, val path: String)

    private suspend fun resolve(uriOrPath: String): Target {
        val (accountId, path) = split(uriOrPath)
        val account = accounts.accounts.first().firstOrNull { it.id == accountId }
            ?: throw IOException("Unknown account for $uriOrPath")
        val password = accounts.passwordOf(accountId) ?: throw IOException("No stored password for ${account.label}")
        return Target(account, WebDavSession(account.baseUrl.toHttpUrl(), account.username, password), path)
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
