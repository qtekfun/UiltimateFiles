package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Checks the SMB server, share and login work and stores the account. */
class SmbAccountService(
    private val accounts: AccountRepository,
    private val connector: SmbConnector = SmbConnector(),
) {
    suspend fun connect(
        host: String,
        port: Int,
        share: String,
        domain: String,
        username: String,
        password: String,
        label: String,
    ): Result<WebDavAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanHost = host.trim()
            val cleanShare = share.trim().trim('/', '\\')
            require(cleanHost.isNotEmpty() && ' ' !in cleanHost && '/' !in cleanHost) { "Not a valid host: $host" }
            require(cleanShare.isNotEmpty() && '/' !in cleanShare && '\\' !in cleanShare) { "Not a valid share name: $share" }
            require(port in 1..65535) { "Not a valid port: $port" }
            connector.connect(cleanHost, port, cleanShare, domain.trim(), username, password).use { handle ->
                handle.share.list("") // proves the share can be read, not just that the login worked
            }
            val account = WebDavAccount(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifEmpty { "$cleanHost/$cleanShare" },
                baseUrl = "smb://$cleanHost:$port/$cleanShare",
                username = if (domain.isBlank()) username else "${domain.trim()}\\$username",
                protocol = AccountProtocol.SMB,
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
