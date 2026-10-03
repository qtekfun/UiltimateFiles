package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class RecordingAccounts : AccountRepository {
    val passwords = mutableMapOf<String, String>()
    private val state = MutableStateFlow(emptyList<WebDavAccount>())
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String): String? = passwords[accountId]
    override suspend fun add(account: WebDavAccount, password: String) {
        passwords[account.id] = password
        state.value += account
    }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
}

class NextcloudLoginFlowTest {
    private lateinit var server: FakeNextcloud

    @Before fun setUp() { server = FakeNextcloud() }
    @After fun tearDown() = server.close()

    private fun flow(timeoutMillis: Long = 5_000) =
        NextcloudLoginFlow(requireHttps = false, pollIntervalMillis = 20, timeoutMillis = timeoutMillis)

    @Test fun startReturnsTheBrowserPageAndPollsWhilePending() = runBlocking {
        val login = flow()
        val start = login.start(server.rootUrl)
        assertEquals("${server.rootUrl}/index.php/login/v2/flow/tok123", start.loginUrl)
        assertNull(login.poll(start))
    }

    @Test fun credentialsArriveOnceTheUserApproves() = runBlocking {
        val login = flow()
        val start = login.start(server.rootUrl + "/remote.php/dav/files/alice") // a pasted DAV URL still works
        val pending = async { login.awaitCredentials(start) }
        delay(150)
        server.approveLogin()
        val credentials = pending.await()
        assertEquals("alice", credentials.loginName)
        assertEquals("secret", credentials.appPassword)
        assertEquals(server.rootUrl, credentials.server)
        assertTrue(server.pollCount.get() >= 2)
    }

    @Test fun neverApprovedExpires() {
        val login = flow(timeoutMillis = 100)
        assertThrows(LoginFlowExpiredException::class.java) {
            runBlocking { login.awaitCredentials(login.start(server.rootUrl)) }
        }
    }

    @Test fun httpIsRefusedByDefault() {
        assertThrows(InvalidServerUrlException::class.java) {
            runBlocking { NextcloudLoginFlow().start(server.rootUrl) }
        }
    }

    @Test fun serviceStoresTheAppPasswordNotAnythingTyped() = runBlocking {
        val accounts = RecordingAccounts()
        val service = WebDavAccountService(
            accounts,
            WebDavClient(retryDelayMillis = 1),
            flow(),
            requireHttps = false,
        )
        val account = service.connectWithLoginFlow(server.rootUrl, "Home") { url ->
            assertTrue(url.endsWith("/login/v2/flow/tok123"))
            server.approveLogin() // the user taps "Allow" in the browser
        }.getOrThrow()
        assertEquals("Home", account.label)
        assertEquals("alice", account.username)
        assertEquals(server.baseUrl, account.baseUrl)
        assertEquals("secret", accounts.passwords[account.id])
    }
}
