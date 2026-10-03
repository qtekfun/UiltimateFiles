package com.qtekfun.ultimatefiles.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Where the user must approve the app, and how to ask the server whether they did. */
class LoginFlowStart(
    val loginUrl: String,
    val pollEndpoint: String,
    val token: String,
    /** How the server was trusted; the polling uses the same. */
    val trust: TrustChoice = TrustChoice(),
)

/** What the server hands out once the user approved: a dedicated app password, never the real one. */
class LoginFlowCredentials(val server: String, val loginName: String, val appPassword: String)

/** The user did not approve in time. */
class LoginFlowExpiredException : IOException("The sign-in was not approved in time")

/**
 * Nextcloud Login Flow v2: the user signs in (password, 2FA, SSO…) in their browser and approves UltimateFiles, which then
 * receives an app password it can revoke from the server. UltimateFiles never sees the account password.
 */
class NextcloudLoginFlow(
    private val http: OkHttpClient = WebDavClient.defaultHttpClient(),
    private val requireHttps: Boolean = true,
    private val pollIntervalMillis: Long = 2_000,
    private val timeoutMillis: Long = 20 * 60_000L, // the server forgets the flow after 20 minutes
) {
    suspend fun start(serverUrl: String, trust: TrustChoice = TrustChoice()): LoginFlowStart = withContext(Dispatchers.IO) {
        val httpsOnly = requireHttps && !trust.allowInsecureHttp
        val root = serverRoot(serverUrl, httpsOnly)
        val request = Request.Builder().url("$root/index.php/login/v2")
            .header("User-Agent", USER_AGENT) // shown to the user as the device name
            .post(FormBody.Builder().build())
            .build()
        val client = PinnedTls.clientFor(http, trust.pinnedSha256)
        val response = try {
            client.newCall(request).execute()
        } catch (e: javax.net.ssl.SSLException) {
            throw untrustedOrOriginal(e, root, trust)
        }
        response.use {
            if (!it.isSuccessful) throw WebDavException(it.code, "Login flow not available (${it.code})")
            val json = JSONObject(it.body!!.string())
            val poll = json.getJSONObject("poll")
            val start = LoginFlowStart(json.getString("login"), poll.getString("endpoint"), poll.getString("token"), trust)
            if (httpsOnly && !(start.loginUrl.startsWith("https://") && start.pollEndpoint.startsWith("https://"))) {
                throw InsecureServerException("The server answered with a non-HTTPS address")
            }
            start
        }
    }

    /** A handshake failure against an unpinned server becomes a question for the user, with the certificate to look at. */
    private fun untrustedOrOriginal(e: javax.net.ssl.SSLException, root: String, trust: TrustChoice): Exception {
        if (trust.pinnedSha256 != null) return e // the pinned certificate changed: that is a warning, not a question
        val url = root.toHttpUrlOrNull() ?: return e
        return try {
            UntrustedCertificateException(PinnedTls.probe(url.host, url.port))
        } catch (probeFailure: Exception) {
            e
        }
    }

    /** One poll: the credentials once approved, `null` while the user is still deciding. */
    suspend fun poll(start: LoginFlowStart): LoginFlowCredentials? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(start.pollEndpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("token", start.token).build())
            .build()
        PinnedTls.clientFor(http, start.trust.pinnedSha256).newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> {
                    val json = JSONObject(response.body!!.string())
                    LoginFlowCredentials(
                        server = json.getString("server").trimEnd('/'),
                        loginName = json.getString("loginName"),
                        appPassword = json.getString("appPassword"),
                    )
                }
                else -> throw WebDavException(response.code, "Login flow failed (${response.code})")
            }
        }
    }

    /** Polls until approved; throws [LoginFlowExpiredException] after the timeout and honours cancellation. */
    suspend fun awaitCredentials(start: LoginFlowStart): LoginFlowCredentials {
        var waited = 0L
        while (waited <= timeoutMillis) {
            try {
                poll(start)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (e is WebDavException) throw e // a refusal is final; network blips are retried
            }
            delay(pollIntervalMillis)
            waited += pollIntervalMillis
        }
        throw LoginFlowExpiredException()
    }

    private fun serverRoot(serverUrl: String, httpsOnly: Boolean): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrlOrNull() ?: throw InvalidServerUrlException("Not a valid address: $serverUrl")
        if (httpsOnly && !parsed.isHttps) throw InsecureServerException("Only HTTPS addresses are supported")
        // Accept a pasted DAV or login URL: the flow lives at the Nextcloud root.
        return trimmed.substringBefore("/remote.php").substringBefore("/index.php").trimEnd('/')
    }

    private companion object {
        const val USER_AGENT = "UltimateFiles"
    }
}
