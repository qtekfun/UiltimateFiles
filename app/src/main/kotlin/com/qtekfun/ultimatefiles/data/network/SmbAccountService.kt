package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Checks the SMB server, share and login work and stores the account. */
class SmbAccountService(
    private val accounts: AccountRepository,
    private val connector: SmbConnector = SmbConnector(),
    /** Called with the id of an account whose data was replaced, so the open connection to it is dropped. */
    private val onAccountChanged: (String) -> Unit = {},
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
            val checked = verify(host, port, share, domain, username, password)
            val account = WebDavAccount(
                id = UUID.randomUUID().toString(),
                label = label.trim().ifEmpty { "${checked.host}/${checked.share}" },
                baseUrl = "smb://${checked.host}:$port/${checked.share}",
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

    /**
     * Changes an existing account in place (same id) once the new data has been checked; if it does not work the
     * stored account is left as it was. An empty [typedPassword] keeps the stored one.
     */
    suspend fun update(
        existing: WebDavAccount,
        host: String,
        port: Int,
        share: String,
        domain: String,
        username: String,
        typedPassword: String,
        label: String,
    ): Result<WebDavAccount> = withContext(Dispatchers.IO) {
        try {
            val stored = accounts.passwordOf(existing.id)
            val password = typedPassword.ifEmpty { stored ?: throw IOException("No stored password for ${existing.label}") }
            val checked = verify(host, port, share, domain, username, password)
            val account = existing.copy(
                label = label.trim().ifEmpty { "${checked.host}/${checked.share}" },
                baseUrl = "smb://${checked.host}:$port/${checked.share}",
                username = if (domain.isBlank()) username else "${domain.trim()}\\$username",
            )
            accounts.update(account, typedPassword.ifEmpty { null })
            onAccountChanged(existing.id)
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class Checked(val host: String, val share: String)

    /** Validates the fields and proves the share can be read, not just that the login worked. */
    private fun verify(host: String, port: Int, share: String, domain: String, username: String, password: String): Checked {
        val cleanHost = host.trim()
        val cleanShare = share.trim().trim('/', '\\')
        require(cleanHost.isNotEmpty() && ' ' !in cleanHost && '/' !in cleanHost) { "Not a valid host: $host" }
        require(cleanShare.isNotEmpty() && '/' !in cleanShare && '\\' !in cleanShare) { "Not a valid share name: $share" }
        require(port in 1..65535) { "Not a valid port: $port" }
        connector.connect(cleanHost, port, cleanShare, domain.trim(), username, password).use { handle ->
            handle.share.list("")
        }
        return Checked(cleanHost, cleanShare)
    }
}
