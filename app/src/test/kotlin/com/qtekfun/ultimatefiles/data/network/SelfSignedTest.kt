package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.data.repository.WebDavFileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLException

private class OneAccount(account: WebDavAccount) : AccountRepository {
    val state = MutableStateFlow(listOf(account))
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String): String? = "secret"
    override suspend fun add(account: WebDavAccount, password: String) { state.value += account }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
    override suspend fun rename(accountId: String, label: String) { state.value = state.value.map { if (it.id == accountId) it.copy(label = label) else it } }
}

private class MemoryAccountStore : AccountRepository {
    val state = MutableStateFlow(emptyList<WebDavAccount>())
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String): String? = "secret"
    override suspend fun add(account: WebDavAccount, password: String) { state.value += account }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
    override suspend fun rename(accountId: String, label: String) { state.value = state.value.map { if (it.id == accountId) it.copy(label = label) else it } }
}

class SelfSignedTest {
    private lateinit var server: FakeNextcloud
    private val client = WebDavClient(retryDelayMillis = 1)

    @Before fun setUp() { server = FakeNextcloud(tls = true) }

    @After fun tearDown() = server.close()

    private fun session() = WebDavSession(server.baseUrl.toHttpUrl(), "alice", "secret")


    @Test fun `a self-signed server is refused without a pin`() {
        assertThrows(SSLException::class.java) { client.propfind(session(), "", 0) }
    }

    @Test fun `pinning its certificate lets the connection through`() {
        val pinned = client.pinnedTo(server.certificateSha256)
        assertTrue(pinned.propfind(session(), "", 0).isNotEmpty())
    }

    @Test fun `the fingerprint is accepted in any notation`() {
        val colons = PinnedTls.display(server.certificateSha256)
        assertTrue(colons.contains(':'))
        assertTrue(client.pinnedTo(colons).propfind(session(), "", 0).isNotEmpty())
        assertTrue(client.pinnedTo(colons.lowercase().replace(":", " ")).propfind(session(), "", 0).isNotEmpty())
    }

    @Test fun `a different certificate than the pinned one is rejected`() {
        val wrong = "00".repeat(32)
        assertThrows(SSLException::class.java) { client.pinnedTo(wrong).propfind(session(), "", 0) }
    }

    @Test fun `the probe reports the certificate the server presents`() {
        val url = server.rootUrl.removePrefix("https://")
        val host = url.substringBefore(':')
        val port = url.substringAfter(':').toInt()
        val info = PinnedTls.probe(host, port)
        assertEquals(server.certificateSha256, info.sha256)
        assertTrue(info.subject.contains("localhost"))
    }

    @Test fun `connecting to a self-signed server asks about the certificate first`() = runBlocking {
        val store = MemoryAccountStore()
        val service = WebDavAccountService(store, client, NextcloudLoginFlow(pollIntervalMillis = 20, timeoutMillis = 5_000))

        val result = service.connect(server.rootUrl, "alice", "secret", "Home")

        val failure = result.exceptionOrNull()
        assertTrue("expected an untrusted certificate, got $failure", failure is UntrustedCertificateException)
        assertEquals(server.certificateSha256, (failure as UntrustedCertificateException).info.sha256)
        assertTrue(store.state.value.isEmpty())
    }

    @Test fun `after trusting the certificate the account keeps the pin and works`() = runBlocking {
        val store = MemoryAccountStore()
        val service = WebDavAccountService(store, client, NextcloudLoginFlow(pollIntervalMillis = 20, timeoutMillis = 5_000))

        val account = service.connect(server.rootUrl, "alice", "secret", "Home", TrustChoice(pinnedSha256 = server.certificateSha256)).getOrThrow()

        assertEquals(server.certificateSha256, account.pinnedCertSha256)
        assertFalse(account.allowInsecureHttp)
        // And the repository reaches the server with that pin.
        val repository = WebDavFileSystemRepository(OneAccount(account), client)
        assertNotNull(repository.listFiles(WebDavFileSystemRepository.rootOf(account.id)).getOrThrow())
    }

    @Test fun `login flow against a self-signed server also asks, then works with the pin`() = runBlocking {
        val login = NextcloudLoginFlow(pollIntervalMillis = 20, timeoutMillis = 5_000)
        val failure = runCatching { login.start(server.rootUrl) }.exceptionOrNull()
        assertTrue(failure is UntrustedCertificateException)

        val trust = TrustChoice(pinnedSha256 = (failure as UntrustedCertificateException).info.sha256)
        val start = login.start(server.rootUrl, trust)
        server.approveLogin()
        val credentials = login.awaitCredentials(start)
        assertEquals("alice", credentials.loginName)
    }

    @Test fun `a repository refuses an http account that was never allowed to be insecure`() = runBlocking {
        FakeNextcloud().use { plain ->
            val account = WebDavAccount("p1", "Plain", plain.baseUrl, "alice") // allowInsecureHttp stays false
            val repository = WebDavFileSystemRepository(OneAccount(account), client)
            assertTrue(repository.listFiles(WebDavFileSystemRepository.rootOf("p1")).isFailure)

            val allowed = account.copy(allowInsecureHttp = true)
            val ok = WebDavFileSystemRepository(OneAccount(allowed), client)
            assertTrue(ok.listFiles(WebDavFileSystemRepository.rootOf("p1")).isSuccess)
        }
    }

    @Test fun `plain http needs an explicit choice when connecting`() = runBlocking {
        FakeNextcloud().use { plain ->
            val service = WebDavAccountService(MemoryAccountStore(), client, NextcloudLoginFlow(pollIntervalMillis = 20, timeoutMillis = 5_000))
            val refused = service.connect(plain.rootUrl, "alice", "secret", "Plain").exceptionOrNull()
            assertTrue("got $refused", refused is InsecureServerException)

            val accepted = service.connect(plain.rootUrl, "alice", "secret", "Plain", TrustChoice(allowInsecureHttp = true)).getOrThrow()
            assertTrue(accepted.allowInsecureHttp)
        }
    }
}
