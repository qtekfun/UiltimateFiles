package com.qtekfun.ultimatefiles.data.repository

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.data.network.SmbConnector
import com.qtekfun.ultimatefiles.data.network.SmbShareHandle
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * [FileSystemRepository] over SMB (Windows shares, Samba, NAS) for every SMB account. Paths look like
 * `smb://<accountId>/<folder>/<file>`, relative to the share. One connection per account is kept and reopened when it drops.
 * This code has only been compiled and checked for its path handling: no SMB server was available to run it against.
 */
class SmbFileSystemRepository(
    private val accounts: AccountRepository,
    private val connector: SmbConnector = SmbConnector(),
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    override suspend fun volumes(): List<StorageVolume> = smbAccounts().map { account ->
        StorageVolume("smb:${account.id}", account.label, rootOf(account.id), StorageKind.NETWORK, isEjectable = false)
    }

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val (account, path) = target(uriOrPath)
        withShare(account) { share ->
            share.list(smbPath(path)).filter { it.fileName != "." && it.fileName != ".." }.map { info ->
                FileItem(
                    path = pathOf(account.id, join(path, info.fileName)),
                    name = info.fileName,
                    isDirectory = isDirectory(info.fileAttributes),
                    sizeBytes = if (isDirectory(info.fileAttributes)) 0L else info.endOfFile,
                    lastModifiedMillis = info.lastWriteTime.toEpochMillis(),
                    mimeType = if (isDirectory(info.fileAttributes)) null else mimeOf(info.fileName),
                    isHidden = info.fileName.startsWith("."),
                )
            }
        }
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val (account, path) = target(uriOrPath)
        withShare(account) { share ->
            val info = share.getFileInformation(smbPath(path))
            val directory = info.standardInformation.isDirectory
            val name = path.substringAfterLast('/').ifEmpty { account.label }
            FileItem(
                path = pathOf(account.id, path),
                name = name,
                isDirectory = directory,
                sizeBytes = if (directory) 0L else info.standardInformation.endOfFile,
                lastModifiedMillis = info.basicInformation.lastWriteTime.toEpochMillis(),
                mimeType = if (directory) null else mimeOf(name),
                isHidden = name.startsWith("."),
            )
        }
    }

    override suspend fun parentOf(uriOrPath: String): String? {
        val (accountId, path) = split(uriOrPath)
        if (path.isEmpty()) return null
        return pathOf(accountId, path.substringBeforeLast('/', ""))
    }

    override suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, parent) = target(parentUriOrPath)
        val path = join(parent, name)
        withShare(account) { it.mkdir(smbPath(path)) }
        FileItem(pathOf(account.id, path), name, true, 0L, System.currentTimeMillis(), null)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, parent) = target(parentUriOrPath)
        val path = join(parent, name)
        withShare(account) { share ->
            share.openFile(
                smbPath(path),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_CREATE,
                null,
            ).close()
        }
        FileItem(pathOf(account.id, path), name, false, 0L, System.currentTimeMillis(), mimeType)
    }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            val (account, path) = target(item.path)
            withShare(account) { share ->
                if (item.isDirectory) share.rmdir(smbPath(path), true) else share.rm(smbPath(path))
            }
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val (account, path) = target(item.path)
        val renamed = join(path.substringBeforeLast('/', ""), newName)
        withShare(account) { share ->
            share.open(
                smbPath(path),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { it.rename(smbPath(renamed)) }
        }
        item.copy(path = pathOf(account.id, renamed), name = newName)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        val (account, path) = target(item.path)
        val file = withShare(account) { share ->
            share.openFile(
                smbPath(path),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        }
        object : FilterInputStream(file.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    runCatching { file.close() }
                }
            }
        }
    }

    override suspend fun openOutput(
        parentUriOrPath: String,
        name: String,
        mimeType: String,
        overwrite: Boolean,
    ): Result<OutputStream> = ioResult {
        requireValidName(name)
        val (account, parent) = target(parentUriOrPath)
        val path = join(parent, name)
        val file = withShare(account) { share ->
            share.openFile(
                smbPath(path),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                if (overwrite) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE,
                null,
            )
        }
        object : FilterOutputStream(file.outputStream) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

            override fun close() {
                try {
                    super.close()
                } finally {
                    runCatching { file.close() }
                }
            }
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private val connections = ConcurrentHashMap<String, SmbShareHandle>()

    private suspend fun smbAccounts() = accounts.accounts.first().filter { it.protocol == AccountProtocol.SMB }

    private suspend fun target(uriOrPath: String): Pair<WebDavAccount, String> {
        val (accountId, path) = split(uriOrPath)
        val account = smbAccounts().firstOrNull { it.id == accountId } ?: throw IOException("Unknown account for $uriOrPath")
        return account to path
    }

    /** Runs [block] on the account's share, reconnecting once when the connection had been closed by the server. */
    private suspend fun <T> withShare(account: WebDavAccount, block: (com.hierynomus.smbj.share.DiskShare) -> T): T =
        withContext(Dispatchers.IO) {
            val password = accounts.passwordOf(account.id) ?: throw IOException("No stored password for ${account.label}")
            try {
                block(handle(account, password).share)
            } catch (e: IOException) {
                val cached = connections[account.id]
                if (cached != null && cached.isOpen) throw e
                drop(account)
                block(handle(account, password).share)
            }
        }

    private fun handle(account: WebDavAccount, password: String): SmbShareHandle {
        connections[account.id]?.takeIf { it.isOpen }?.let { return it }
        drop(account)
        val (host, port, share) = SmbConnector.parse(account.baseUrl)
        val (domain, user) = SmbConnector.splitLogin(account.username)
        return connector.connect(host, port, share, domain, user, password).also { connections[account.id] = it }
    }

    private fun drop(account: WebDavAccount) = forget(account.id)

    /** Closes the connection kept for an account whose data has just been changed, so the next call uses the new data. */
    fun forget(accountId: String) {
        connections.remove(accountId)?.close()
    }

    private fun isDirectory(attributes: Long) = EnumWithValue.EnumUtils.isSet(attributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    companion object {
        const val SCHEME = "smb://"

        fun rootOf(accountId: String) = "$SCHEME$accountId/"

        fun pathOf(accountId: String, relative: String) = "$SCHEME$accountId/$relative"

        fun split(uriOrPath: String): Pair<String, String> {
            require(uriOrPath.startsWith(SCHEME)) { "Not an SMB path: $uriOrPath" }
            val rest = uriOrPath.removePrefix(SCHEME)
            return rest.substringBefore('/') to rest.substringAfter('/', "").trim('/')
        }

        /** SMB separates folders with backslashes; the app uses slashes everywhere else. */
        fun smbPath(relative: String) = relative.replace('/', '\\')
    }
}
