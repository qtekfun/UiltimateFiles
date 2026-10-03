package com.qtekfun.fexplo.data.network

import com.qtekfun.fexplo.core.model.WebDavAccount
import com.qtekfun.fexplo.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Raised when the address the user typed cannot be used. */
class InvalidServerUrlException(message: String) : IllegalArgumentException(message)

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
        openBrowser: (String) -> Unit,
    ): Result<WebDavAccount> = try {
        val start = loginFlow.start(serverUrl)
        openBrowser(start.loginUrl) // runs in the caller's context (the UI thread for the ViewModel)
        val credentials = loginFlow.awaitCredentials(start)
        connect(credentials.server, credentials.loginName, credentials.appPassword, label)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun connect(serverUrl: String, username: String, password: String, label: String): Result<WebDavAccount> =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = buildBaseUrl(serverUrl, username, requireHttps)
                val session = WebDavSession(baseUrl.toHttpUrlOrNull()!!, username, password)
                client.propfind(session, "", depth = 0) // fails with 401 on bad credentials, 404 on a wrong path
                val account = WebDavAccount(
                    id = UUID.randomUUID().toString(),
                    label = label.trim().ifEmpty { session.baseUrl.host },
                    baseUrl = baseUrl,
                    username = username,
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
            if (requireHttps && !parsed.isHttps) throw InvalidServerUrlException("Only HTTPS addresses are supported")
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
