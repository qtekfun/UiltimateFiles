package com.qtekfun.ultimatefiles.data.network

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An open SMB share and everything that has to be closed with it. */
class SmbShareHandle(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    val share: DiskShare,
) : Closeable {
    val isOpen: Boolean get() = connection.isConnected && share.isConnected

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }
}

/** Opens an SMB share with a user name and password (NTLM). Nothing here guesses: a wrong share or login is an error. */
class SmbConnector {
    fun connect(host: String, port: Int, share: String, domain: String, username: String, password: String): SmbShareHandle {
        val client = SMBClient(
            SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build(),
        )
        try {
            val connection = client.connect(host, port)
            val session = connection.authenticate(AuthenticationContext(username, password.toCharArray(), domain))
            val disk = session.connectShare(share) as? DiskShare ?: throw IOException("$share is not a file share")
            return SmbShareHandle(client, connection, session, disk)
        } catch (e: Exception) {
            runCatching { client.close() }
            throw e
        }
    }

    companion object {
        /** `smb://host:port/share` as stored in the account: host, port (445 by default) and share name. */
        fun parse(baseUrl: String): Triple<String, Int, String> {
            val rest = baseUrl.removePrefix("smb://").trimEnd('/')
            val authority = rest.substringBefore('/')
            val share = rest.substringAfter('/', "")
            val host = authority.substringBeforeLast(':')
            val port = authority.substringAfterLast(':', "").toIntOrNull()
            return if (authority.contains(':') && port != null) Triple(host, port, share) else Triple(authority, 445, share)
        }

        /** The stored login `DOMAIN\user` split into its domain (empty when there is none) and user name. */
        fun splitLogin(login: String): Pair<String, String> =
            if ('\\' in login) login.substringBefore('\\') to login.substringAfter('\\') else "" to login
    }
}
