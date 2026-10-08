package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Checks an SFTP server and password work and stores the account with the host key the user confirmed. */
class SftpAccountService(
    private val accounts: AccountRepository,
    private val connector: SshConnector = SshConnector(),
    /** Called with the id of an account whose data was replaced, so open connections to it are dropped. */
    private val onAccountChanged: (String) -> Unit = {},
) {
    /**
     * Fails with [UntrustedHostKeyException] while [pinnedFingerprint] is null (the caller shows the fingerprint and
     * calls again with it) and with [HostKeyChangedException] when the server presents another key than the pinned one.
     */
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        label: String,
        pinnedFingerprint: String?,
    ): Result<WebDavAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanHost = verify(host, port, username, password, pinnedFingerprint)
            val account = WebDavAccount(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifEmpty { cleanHost },
                baseUrl = "${SftpFileSystemRepository.SCHEME}$cleanHost:$port",
                username = username,
                pinnedCertSha256 = pinnedFingerprint,
                protocol = AccountProtocol.SFTP,
            )
            accounts.add(account, password)
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Changes an existing account in place (same id, so open panels, history and saved folders keep working) once the
     * new data has been checked; if it does not work the stored account is left as it was. [typedSecret] is the
     * password, or the passphrase of the key (see [AccountEditing.sftpSecret]); empty keeps what is stored.
     * [pinnedFingerprint] follows the rules of [connect]; see [AccountEditing.sftpPinFor] for the one to start with.
     */
    suspend fun update(
        existing: WebDavAccount,
        host: String,
        port: Int,
        username: String,
        typedSecret: String,
        newKeyPem: String?,
        useKey: Boolean,
        label: String,
        pinnedFingerprint: String?,
    ): Result<WebDavAccount> = withContext(Dispatchers.IO) {
        try {
            val stored = accounts.passwordOf(existing.id)
            val newSecret = AccountEditing.sftpSecret(stored, typedSecret, newKeyPem, useKey)
            val secret = newSecret ?: stored ?: throw IOException("No stored password for ${existing.label}")
            val cleanHost = verify(host, port, username, secret, pinnedFingerprint)
            val account = existing.copy(
                label = label.trim().ifEmpty { cleanHost },
                baseUrl = "${SftpFileSystemRepository.SCHEME}$cleanHost:$port",
                username = username,
                pinnedCertSha256 = pinnedFingerprint,
            )
            accounts.update(account, newSecret)
            onAccountChanged(existing.id)
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Checks the host and port, logs in and opens the SFTP subsystem; returns the cleaned host. */
    private fun verify(host: String, port: Int, username: String, secret: String, pinnedFingerprint: String?): String {
        val cleanHost = host.trim()
        require(cleanHost.isNotEmpty() && ' ' !in cleanHost && '/' !in cleanHost) { "Not a valid host: $host" }
        require(port in 1..65535) { "Not a valid port: $port" }
        connector.connect(cleanHost, port, username, secret, pinnedFingerprint).use { ssh ->
            ssh.newSFTPClient().use { it.canonicalize(".") } // proves the subsystem works, not just the login
        }
        return cleanHost
    }
}
