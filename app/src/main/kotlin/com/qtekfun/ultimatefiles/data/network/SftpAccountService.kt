package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Checks an SFTP server and password work and stores the account with the host key the user confirmed. */
class SftpAccountService(
    private val accounts: AccountRepository,
    private val connector: SshConnector = SshConnector(),
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
            val cleanHost = host.trim()
            require(cleanHost.isNotEmpty() && ' ' !in cleanHost && '/' !in cleanHost) { "Not a valid host: $host" }
            require(port in 1..65535) { "Not a valid port: $port" }
            connector.connect(cleanHost, port, username, password, pinnedFingerprint).use { ssh ->
                ssh.newSFTPClient().use { it.canonicalize(".") } // proves the subsystem works, not just the login
            }
            val account = WebDavAccount(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifEmpty { cleanHost },
                baseUrl = "${com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository.SCHEME}$cleanHost:$port",
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
}
