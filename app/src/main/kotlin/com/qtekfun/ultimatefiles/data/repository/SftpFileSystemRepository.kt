package com.qtekfun.ultimatefiles.data.repository

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.data.network.SshConnector
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * [FileSystemRepository] over SFTP for every SFTP account. Paths look like `sftp://<accountId>/<folder>/<file>`,
 * relative to the account's home directory. One SSH connection per account is kept and opened again when it drops.
 */
class SftpFileSystemRepository(
    private val accounts: AccountRepository,
    private val connector: SshConnector = SshConnector(),
    private val mimeOf: (String) -> String? = MimeTypes::fromName,
) : FileSystemRepository {

    override suspend fun volumes(): List<StorageVolume> = sftpAccounts().map { account ->
        StorageVolume(
            id = "sftp:${account.id}",
            label = account.label,
            rootPath = rootOf(account.id),
            kind = StorageKind.NETWORK,
            isEjectable = false,
        )
    }

    override suspend fun listFiles(uriOrPath: String): Result<List<FileItem>> = ioResult {
        val (account, path) = target(uriOrPath)
        withSftp(account) { sftp ->
            val dir = remote(sftp, path)
            sftp.ls(dir).filter { it.name != "." && it.name != ".." }.map { info ->
                var attrs = info.attributes
                if (attrs.type == FileMode.Type.SYMLINK) attrs = runCatching { sftp.stat(info.path) }.getOrDefault(attrs)
                toItem(account.id, join(path, info.name), info.name, attrs)
            }
        }
    }

    override suspend fun stat(uriOrPath: String): Result<FileItem> = ioResult {
        val (account, path) = target(uriOrPath)
        withSftp(account) { sftp ->
            toItem(account.id, path, path.substringAfterLast('/'), sftp.stat(remote(sftp, path)))
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
        withSftp(account) { sftp -> sftp.mkdir(remote(sftp, path)) }
        FileItem(pathOf(account.id, path), name, true, 0L, System.currentTimeMillis(), null)
    }

    override suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem> = ioResult {
        requireValidName(name)
        val (account, parent) = target(parentUriOrPath)
        val path = join(parent, name)
        withSftp(account) { sftp ->
            sftp.open(remote(sftp, path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).close()
        }
        FileItem(pathOf(account.id, path), name, false, 0L, System.currentTimeMillis(), mimeType)
    }

    override suspend fun delete(items: List<FileItem>): Result<Unit> = ioResult {
        items.forEach { item ->
            val (account, path) = target(item.path)
            withSftp(account) { sftp -> removeTree(sftp, remote(sftp, path)) }
        }
    }

    override suspend fun rename(item: FileItem, newName: String): Result<FileItem> = ioResult {
        requireValidName(newName)
        val (account, path) = target(item.path)
        val renamed = join(path.substringBeforeLast('/', ""), newName)
        withSftp(account) { sftp -> sftp.rename(remote(sftp, path), remote(sftp, renamed)) }
        item.copy(path = pathOf(account.id, renamed), name = newName)
    }

    override suspend fun openInput(item: FileItem): Result<InputStream> = ioResult {
        val (account, path) = target(item.path)
        val opened = withSftp(account, keepOpen = true) { sftp ->
            val file = sftp.open(remote(sftp, path), EnumSet.of(OpenMode.READ))
            Opened(sftp, file)
        }
        object : FilterInputStream(opened.file.ReadAheadRemoteFileInputStream(READ_AHEAD)) {
            override fun close() = opened.closeAfter { super.close() }
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
        val opened = withSftp(account, keepOpen = true) { sftp ->
            val mode = if (overwrite) {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            } else {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)
            }
            Opened(sftp, sftp.open(remote(sftp, path), mode))
        }
        object : FilterOutputStream(opened.file.RemoteFileOutputStream(0, WRITE_AHEAD)) {
            // FilterOutputStream would write byte by byte; the SFTP stream takes whole buffers.
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

            override fun close() = opened.closeAfter { super.close() }
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    /** An open remote file and the channel it lives on; both close together when the stream does. */
    private class Opened(val sftp: SFTPClient, val file: RemoteFile) {
        fun closeAfter(closeStream: () -> Unit) {
            try {
                closeStream()
            } finally {
                runCatching { file.close() }
                runCatching { sftp.close() }
            }
        }
    }

    private val connections = ConcurrentHashMap<String, SSHClient>()

    private suspend fun sftpAccounts() = accounts.accounts.first().filter { it.protocol == AccountProtocol.SFTP }

    private suspend fun target(uriOrPath: String): Pair<WebDavAccount, String> {
        val (accountId, path) = split(uriOrPath)
        val account = sftpAccounts().firstOrNull { it.id == accountId } ?: throw IOException("Unknown account for $uriOrPath")
        return account to path
    }

    /**
     * Runs [block] on a fresh SFTP channel of the account's connection. The connection is reopened once when it had
     * dropped. With [keepOpen] the channel stays open for the caller (streams), otherwise it is closed afterwards.
     */
    private suspend fun <T> withSftp(account: WebDavAccount, keepOpen: Boolean = false, block: (SFTPClient) -> T): T =
        withContext(Dispatchers.IO) {
            val password = accounts.passwordOf(account.id) ?: throw IOException("No stored password for ${account.label}")
            try {
                runOn(account, password, keepOpen, block)
            } catch (e: IOException) {
                // A connection the server closed fails every call; a failed call on a healthy one is a real error.
                val cached = connections[account.id]
                if (cached != null && cached.isConnected && cached.isAuthenticated) throw e
                drop(account)
                runOn(account, password, keepOpen, block)
            }
        }

    private fun <T> runOn(account: WebDavAccount, password: String, keepOpen: Boolean, block: (SFTPClient) -> T): T {
        val sftp = connection(account, password).newSFTPClient()
        var ok = false
        try {
            return block(sftp).also { ok = true }
        } finally {
            if (!keepOpen || !ok) runCatching { sftp.close() }
        }
    }

    private fun connection(account: WebDavAccount, password: String): SSHClient {
        connections[account.id]?.takeIf { it.isConnected && it.isAuthenticated }?.let { return it }
        drop(account)
        val (host, port) = hostAndPort(account.baseUrl)
        val ssh = connector.connect(host, port, account.username, password, account.pinnedCertSha256)
        connections[account.id] = ssh
        return ssh
    }

    private fun drop(account: WebDavAccount) = forget(account.id)

    /** Closes the connection kept for an account whose data has just been changed, so the next call uses the new data. */
    fun forget(accountId: String) {
        connections.remove(accountId)?.let { runCatching { it.close() } }
    }

    /** The account's home directory joined with [relative]; the home is asked once per channel, which is cheap. */
    private fun remote(sftp: SFTPClient, relative: String): String {
        val home = sftp.canonicalize(".")
        return if (relative.isEmpty()) home else "${home.trimEnd('/')}/$relative"
    }

    private fun removeTree(sftp: SFTPClient, path: String) {
        val attrs = sftp.lstat(path)
        if (attrs.type == FileMode.Type.DIRECTORY) {
            sftp.ls(path).filter { it.name != "." && it.name != ".." }.forEach { removeTree(sftp, it.path) }
            sftp.rmdir(path)
        } else {
            sftp.rm(path)
        }
    }

    private fun toItem(accountId: String, path: String, name: String, attrs: FileAttributes): FileItem {
        val directory = attrs.type == FileMode.Type.DIRECTORY
        return FileItem(
            path = pathOf(accountId, path),
            name = name,
            isDirectory = directory,
            sizeBytes = if (directory) 0L else attrs.size,
            lastModifiedMillis = attrs.mtime * 1000L,
            mimeType = if (directory) null else mimeOf(name),
            isHidden = name.startsWith("."),
        )
    }

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    companion object {
        const val SCHEME = "sftp://"
        private const val READ_AHEAD = 16
        private const val WRITE_AHEAD = 16

        fun rootOf(accountId: String) = "$SCHEME$accountId/"

        fun pathOf(accountId: String, relative: String) = "$SCHEME$accountId/$relative"

        fun split(uriOrPath: String): Pair<String, String> {
            require(uriOrPath.startsWith(SCHEME)) { "Not an SFTP path: $uriOrPath" }
            val rest = uriOrPath.removePrefix(SCHEME)
            return rest.substringBefore('/') to rest.substringAfter('/', "").trim('/')
        }

        /** `sftp://host:port` as stored in [WebDavAccount.baseUrl]. */
        fun hostAndPort(baseUrl: String): Pair<String, Int> {
            val authority = baseUrl.removePrefix(SCHEME).trimEnd('/')
            val host = authority.substringBeforeLast(':')
            val port = authority.substringAfterLast(':', "").toIntOrNull() ?: 22
            return (if (authority.contains(':')) host else authority) to port
        }
    }
}
