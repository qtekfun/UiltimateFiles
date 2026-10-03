package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Raised when the address the user typed cannot be used. */
open class InvalidServerUrlException(message: String) : IllegalArgumentException(message)

/** The address is a plain `http://` one: usable only if the user explicitly accepts sending everything unencrypted. */
class InsecureServerException(message: String) : InvalidServerUrlException(message)

/**
 * Turns what the user typed into a DAV root and checks it works before saving.
 * A bare server address is assumed to be Nextcloud/ownCloud; a URL that already points at a DAV endpoint is used as is.
 */
class WebDavAccountService(
    private val accounts: AccountRepository,
    private val client: WebDavClient = WebDavClient(),
    private val loginFlow: NextcloudLoginFlow = NextcloudLoginFlow(),
    private val requireHttps: Boolean = true,
) {
    /**
     * Signs in through Nextcloud's Login Flow v2: [openBrowser] receives the page where the user approves
     * UltimateFiles, then this suspends until they do (or the flow expires) and stores the app password the server issues.
     */
    suspend fun connectWithLoginFlow(
        serverUrl: String,
        label: String,
        trust: TrustChoice = TrustChoice(),
        openBrowser: (String) -> Unit,
    ): Result<WebDavAccount> = try {
        val start = loginFlow.start(serverUrl, trust)
        openBrowser(start.loginUrl) // runs in the caller's context (the UI thread for the ViewModel)
        val credentials = loginFlow.awaitCredentials(start)
        connect(credentials.server, credentials.loginName, credentials.appPassword, label, trust)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun connect(
        serverUrl: String,
        username: String,
        password: String,
        label: String,
        trust: TrustChoice = TrustChoice(),
    ): Result<WebDavAccount> =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = buildBaseUrl(serverUrl, username, requireHttps && !trust.allowInsecureHttp)
                val session = WebDavSession(baseUrl.toHttpUrlOrNull()!!, username, password)
                try {
                    // Fails with 401 on bad credentials, 404 on a wrong path.
                    client.pinnedTo(trust.pinnedSha256).propfind(session, "", depth = 0)
                } catch (e: javax.net.ssl.SSLException) {
                    if (trust.pinnedSha256 != null) throw e
                    throw runCatching { UntrustedCertificateException(PinnedTls.probe(session.baseUrl.host, session.baseUrl.port)) }
                        .getOrDefault(e)
                }
                val account = WebDavAccount(
                    id = UUID.randomUUID().toString(),
                    label = label.trim().ifEmpty { session.baseUrl.host },
                    baseUrl = baseUrl,
                    username = username,
                    pinnedCertSha256 = trust.pinnedSha256?.let(PinnedTls::normalize),
                    allowInsecureHttp = trust.allowInsecureHttp,
                )
                accounts.add(account, password)
                Result.success(account)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        /** Returns the DAV files root for [serverUrl] and [username], or throws [InvalidServerUrlException]. */
        fun buildBaseUrl(serverUrl: String, username: String, requireHttps: Boolean = true): String {
            val trimmed = serverUrl.trim().trimEnd('/')
            val parsed = trimmed.toHttpUrlOrNull() ?: throw InvalidServerUrlException("Not a valid address: $serverUrl")
            if (requireHttps && !parsed.isHttps) throw InsecureServerException("Only HTTPS addresses are supported")
            val path = parsed.encodedPath
            val alreadyDav = path.contains("remote.php") || path.contains("/dav") || path.contains("webdav")
            if (alreadyDav) return trimmed
            require(username.isNotBlank()) { "Username is required" }
            return parsed.newBuilder()
                .addPathSegments("remote.php/dav/files")
                .addPathSegment(username.trim())
                .build()
                .toString()
                .trimEnd('/')
        }
    }
}
