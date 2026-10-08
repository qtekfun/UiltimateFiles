package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository

/** What the "add account" form shows for an account that already exists (everything but its secret). */
data class AccountFormValues(
    val protocol: AccountProtocol,
    val label: String,
    /** Nextcloud/WebDAV: the server address as the user would type it, without the DAV path. */
    val server: String = "",
    val host: String = "",
    val port: Int = 0,
    val share: String = "",
    val domain: String = "",
    val username: String = "",
)

/**
 * The rules of editing an account, kept free of Android and the network so they can be tested: how the stored data maps
 * back to form fields, which of the old trust decisions still hold for a new address, and what the stored secret becomes.
 */
object AccountEditing {

    fun formOf(account: WebDavAccount): AccountFormValues = when (account.protocol) {
        AccountProtocol.WEBDAV -> AccountFormValues(
            protocol = account.protocol,
            label = account.label,
            server = serverRootOf(account.baseUrl),
            username = account.username,
        )
        AccountProtocol.SFTP -> {
            val (host, port) = SftpFileSystemRepository.hostAndPort(account.baseUrl)
            AccountFormValues(account.protocol, account.label, host = host, port = port, username = account.username)
        }
        AccountProtocol.SMB -> {
            val (host, port, share) = SmbConnector.parse(account.baseUrl)
            val (domain, user) = SmbConnector.splitLogin(account.username)
            AccountFormValues(account.protocol, account.label, host = host, port = port, share = share, domain = domain, username = user)
        }
    }

    /**
     * `https://cloud.example.com/remote.php/dav/files/alice` is shown as `https://cloud.example.com` (also when the server
     * lives in a sub-folder); an address that does not look like Nextcloud's is shown as it is.
     */
    fun serverRootOf(baseUrl: String): String {
        val index = baseUrl.indexOf("/remote.php/")
        return (if (index >= 0) baseUrl.substring(0, index) else baseUrl).trimEnd('/')
    }

    /** Whether [typed] is the same server as the one [existingBaseUrl] points at (case of the host and trailing slashes aside). */
    fun sameServer(existingBaseUrl: String, typed: String): Boolean = normalize(serverRootOf(existingBaseUrl)) == normalize(typed)

    private fun normalize(address: String): String {
        val trimmed = address.trim().trimEnd('/')
        val scheme = trimmed.substringBefore("://", "")
        val rest = trimmed.substringAfter("://", trimmed)
        val authority = rest.substringBefore('/')
        val path = rest.substring(authority.length)
        return (if (scheme.isEmpty()) "" else scheme.lowercase() + "://") + authority.lowercase() + path
    }

    /**
     * The certificate and HTTP decisions of the old account only hold for the same server: another address starts from
     * nothing and the user is asked again.
     */
    fun trustFor(existing: WebDavAccount, newServer: String): TrustChoice =
        if (sameServer(existing.baseUrl, newServer)) {
            TrustChoice(existing.pinnedCertSha256, existing.allowInsecureHttp)
        } else {
            TrustChoice()
        }

    /**
     * The pinned SSH host key of an SFTP account holds only for the same host and port; a different server must be
     * confirmed again, so nothing is pinned (null) and the connection asks.
     */
    fun sftpPinFor(existing: WebDavAccount, host: String, port: Int): String? {
        val (oldHost, oldPort) = SftpFileSystemRepository.hostAndPort(existing.baseUrl)
        return if (oldHost.equals(host.trim(), ignoreCase = true) && oldPort == port) existing.pinnedCertSha256 else null
    }

    /**
     * What the stored SFTP secret becomes, or null to keep it as it is. [existingSecret] is a password, or a private key
     * with its passphrase after a NUL character. With [useKey]: a newly picked [newKeyPem] replaces the key (and [typed]
     * is its passphrase), otherwise the stored key stays and a non-empty [typed] is its new passphrase. Without it, [typed]
     * is the password; empty means "keep" only while the account already used a password.
     */
    fun sftpSecret(existingSecret: String?, typed: String, newKeyPem: String?, useKey: Boolean): String? {
        val existingKey = existingSecret?.let(SshConnector::privateKeyOf)
        if (useKey) {
            if (newKeyPem != null) return if (typed.isEmpty()) newKeyPem else newKeyPem + "\u0000" + typed
            val key = existingKey ?: return null
            return if (typed.isEmpty()) null else key.first + "\u0000" + typed
        }
        if (typed.isNotEmpty()) return typed
        return if (existingKey == null) null else throw IllegalArgumentException("A password is required to stop using a key")
    }
}
