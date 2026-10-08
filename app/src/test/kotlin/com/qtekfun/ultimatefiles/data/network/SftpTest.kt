package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.RoutingFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class MemoryAccounts : AccountRepository {
    val state = MutableStateFlow<List<WebDavAccount>>(emptyList())
    private val passwords = HashMap<String, String>()
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String) = passwords[accountId]
    override suspend fun add(account: WebDavAccount, password: String) {
        state.value = state.value.filterNot { it.id == account.id } + account
        passwords[account.id] = password
    }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
    override suspend fun update(account: WebDavAccount, password: String?) {
        if (state.value.none { it.id == account.id }) return
        state.value = state.value.map { if (it.id == account.id) account else it }
        if (password != null) passwords[account.id] = password
    }
    override suspend fun rename(accountId: String, label: String) { state.value = state.value.map { if (it.id == accountId) it.copy(label = label) else it } }
}

/** Runs the real SFTP code against an embedded Apache MINA SSH server whose root is a temporary folder. */
class SftpTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: SshServer
    private lateinit var root: File
    private val accounts = MemoryAccounts()
    private val connector = SshConnector.forJvm()

    @Volatile private var validPassword = "secret"

    @Before
    fun setUp() {
        root = tmp.newFolder("server-root")
        server = SshServer.setUpDefaultServer().apply {
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(tmp.newFile("hostkey.ser").toPath())
            setPasswordAuthenticator { user, password, _ -> user == "alice" && password == validPassword }
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(root.toPath())
            start()
        }
    }

    @After
    fun tearDown() {
        server.stop(true)
    }

    private val service get() = SftpAccountService(accounts, connector)

    private suspend fun connectTrusted(): WebDavAccount {
        val first = service.connect("127.0.0.1", server.port, "alice", "secret", "Lab", null)
        val fingerprint = (first.exceptionOrNull() as UntrustedHostKeyException).fingerprint
        return service.connect("127.0.0.1", server.port, "alice", "secret", "Lab", fingerprint).getOrThrow()
    }

    @Test
    fun `an unknown host key is reported and nothing is stored`() = runTest {
        val result = service.connect("127.0.0.1", server.port, "alice", "secret", "Lab", null)

        val failure = result.exceptionOrNull()
        assertTrue(failure is UntrustedHostKeyException)
        assertTrue((failure as UntrustedHostKeyException).fingerprint.startsWith("SHA256:"))
        assertTrue(accounts.state.value.isEmpty())
    }

    @Test
    fun `the confirmed host key is stored with the account`() = runTest {
        val account = connectTrusted()

        assertEquals(AccountProtocol.SFTP, account.protocol)
        assertEquals("sftp://127.0.0.1:${server.port}", account.baseUrl)
        assertTrue(account.pinnedCertSha256!!.startsWith("SHA256:"))
        assertEquals(account, accounts.accounts.first().single())
    }

    @Test
    fun `a different host key than the pinned one is refused`() = runTest {
        val result = service.connect("127.0.0.1", server.port, "alice", "secret", "Lab", "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

        assertTrue(result.exceptionOrNull() is HostKeyChangedException)
        assertTrue(accounts.state.value.isEmpty())
    }

    @Test
    fun `a wrong password fails and stores nothing`() = runTest {
        val first = service.connect("127.0.0.1", server.port, "alice", "secret", "Lab", null)
        val fingerprint = (first.exceptionOrNull() as UntrustedHostKeyException).fingerprint

        val result = service.connect("127.0.0.1", server.port, "alice", "wrong", "Lab", fingerprint)

        assertTrue(result.isFailure)
        assertTrue(accounts.state.value.isEmpty())
    }

    @Test
    fun `lists creates renames and deletes through the repository`() = runTest {
        val account = connectTrusted()
        File(root, "docs").mkdirs()
        File(root, "docs/readme.txt").writeText("hello")
        val repo = SftpFileSystemRepository(accounts, connector)
        val rootPath = SftpFileSystemRepository.rootOf(account.id)

        assertEquals(listOf("docs"), repo.listFiles(rootPath).getOrThrow().map { it.name })
        val docs = repo.listFiles(rootPath).getOrThrow().single()
        assertTrue(docs.isDirectory)
        val readme = repo.listFiles(docs.path).getOrThrow().single()
        assertEquals(5L, readme.sizeBytes)
        assertEquals(docs.path.removeSuffix("/"), repo.parentOf(readme.path))

        val made = repo.createDirectory(rootPath, "new").getOrThrow()
        assertTrue(File(root, "new").isDirectory)
        val renamed = repo.rename(made, "renamed").getOrThrow()
        assertTrue(File(root, "renamed").isDirectory)
        assertFalse(File(root, "new").exists())

        repo.delete(listOf(docs, renamed)).getOrThrow()
        assertFalse(File(root, "docs").exists())
        assertFalse(File(root, "renamed").exists())
    }

    @Test
    fun `the transfer engine uploads and downloads with verification`() = runTest {
        val account = connectTrusted()
        val local = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val sftp = SftpFileSystemRepository(accounts, connector)
        val router = RoutingFileSystemRepository(local, local, local, sftp, local)
        val engine = TransferEngine(router, FileStreamCopier(), bigFileBytes = 2_000)
        val source = tmp.newFolder("footage")
        val bytes = ByteArray(4_500) { (it * 7).toByte() }
        File(source, "a.bin").writeBytes(bytes)
        File(source, "sub").mkdirs()
        File(source, "sub/b.txt").writeText("hello")
        val items = listOf(local.stat(source.path).getOrThrow())
        val remoteRoot = SftpFileSystemRepository.rootOf(account.id)

        val up = engine.execute(TransferRequest(OperationType.COPY, items, remoteRoot, verify = true)) { _, _ ->
            error("no conflict expected")
        }.toList()

        assertEquals(up.last().error, TransferStatus.COMPLETED, up.last().status)
        assertArrayEquals(bytes, File(root, "footage/a.bin").readBytes())
        assertEquals("hello", File(root, "footage/sub/b.txt").readText())
        assertTrue(root.walkTopDown().none { it.name.endsWith(TransferEngine.PART_SUFFIX) })

        val back = tmp.newFolder("restore")
        val remoteFolder = sftp.listFiles(remoteRoot).getOrThrow().single { it.name == "footage" }
        val down = engine.execute(TransferRequest(OperationType.COPY, listOf(remoteFolder), back.path, verify = true)) { _, _ ->
            error("no conflict expected")
        }.toList()

        assertEquals(down.last().error, TransferStatus.COMPLETED, down.last().status)
        assertArrayEquals(bytes, File(back, "footage/a.bin").readBytes())
    }

    @Test
    fun `tells a private key from a password`() {
        assertEquals(null, SshConnector.privateKeyOf("secret"))
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----\nabc" to null, SshConnector.privateKeyOf("-----BEGIN OPENSSH PRIVATE KEY-----\nabc"))
        assertEquals("-----BEGIN RSA PRIVATE KEY-----\nabc" to "pass", SshConnector.privateKeyOf("-----BEGIN RSA PRIVATE KEY-----\nabc\u0000pass"))
    }

    @Test
    fun `logs in with a private key instead of a password`() = runTest {
        val pair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        server.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { user, key, _ ->
            user == "alice" && key.encoded.contentEquals(pair.public.encoded)
        }
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        val first = service.connect("127.0.0.1", server.port, "alice", pem, "Key", null)
        val fingerprint = (first.exceptionOrNull() as UntrustedHostKeyException).fingerprint

        val account = service.connect("127.0.0.1", server.port, "alice", pem, "Key", fingerprint).getOrThrow()

        assertEquals(AccountProtocol.SFTP, account.protocol)
        assertEquals(pem, accounts.passwordOf(account.id))
    }

    // --- editing an account ---------------------------------------------------------------------

    private val changed = mutableListOf<String>()
    private val editingService get() = SftpAccountService(accounts, connector) { changed += it }

    @Test
    fun `editing keeps the id and the stored password when none is typed`() = runTest {
        val account = connectTrusted()

        val result = editingService.update(
            account, "127.0.0.1", server.port, "alice", typedSecret = "", newKeyPem = null, useKey = false,
            label = "Renamed", pinnedFingerprint = AccountEditing.sftpPinFor(account, "127.0.0.1", server.port),
        )

        val saved = result.getOrThrow()
        assertEquals(account.id, saved.id)
        assertEquals("Renamed", saved.label)
        assertEquals(saved, accounts.accounts.first().single())
        assertEquals("secret", accounts.passwordOf(account.id))
        assertEquals(account.pinnedCertSha256, saved.pinnedCertSha256)
        assertEquals(listOf(account.id), changed)
    }

    @Test
    fun `editing with a new password checks it and replaces the stored one`() = runTest {
        val account = connectTrusted()
        validPassword = "secret2"

        val result = editingService.update(
            account, "127.0.0.1", server.port, "alice", typedSecret = "secret2", newKeyPem = null, useKey = false,
            label = "Lab", pinnedFingerprint = account.pinnedCertSha256,
        )

        assertTrue(result.isSuccess)
        assertEquals("secret2", accounts.passwordOf(account.id))
    }

    @Test
    fun `a failed check leaves the stored account exactly as it was`() = runTest {
        val account = connectTrusted()

        val result = editingService.update(
            account, "127.0.0.1", server.port, "mallory", typedSecret = "", newKeyPem = null, useKey = false,
            label = "Broken", pinnedFingerprint = account.pinnedCertSha256,
        )

        assertTrue(result.isFailure)
        assertEquals(account, accounts.accounts.first().single())
        assertEquals("secret", accounts.passwordOf(account.id))
        assertTrue(changed.isEmpty())
    }

    @Test
    fun `another host key than the pinned one is refused and nothing changes`() = runTest {
        val account = connectTrusted()

        val result = editingService.update(
            account, "127.0.0.1", server.port, "alice", typedSecret = "", newKeyPem = null, useKey = false,
            label = "Lab", pinnedFingerprint = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        )

        assertTrue(result.exceptionOrNull() is HostKeyChangedException)
        assertEquals(account, accounts.accounts.first().single())
    }

    @Test
    fun `a different host starts with nothing pinned, asks, and then keeps the id`() = runTest {
        val account = connectTrusted()
        val pin = AccountEditing.sftpPinFor(account, "localhost", server.port)
        assertNull(pin)

        val asked = editingService.update(
            account, "localhost", server.port, "alice", typedSecret = "", newKeyPem = null, useKey = false, label = "Lab", pinnedFingerprint = pin,
        )
        val fingerprint = (asked.exceptionOrNull() as UntrustedHostKeyException).fingerprint
        assertEquals("the old account stays until the new host key is confirmed", account, accounts.accounts.first().single())

        val saved = editingService.update(
            account, "localhost", server.port, "alice", typedSecret = "", newKeyPem = null, useKey = false, label = "Lab", pinnedFingerprint = fingerprint,
        ).getOrThrow()

        assertEquals(account.id, saved.id)
        assertEquals("sftp://localhost:${server.port}", saved.baseUrl)
        assertEquals(fingerprint, saved.pinnedCertSha256)
    }

    @Test
    fun `leaving a key for a password needs a password and changes nothing without one`() = runTest {
        val pem = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n"
        val keyed = WebDavAccount("k", "Key", "sftp://127.0.0.1:${server.port}", "alice", pinnedCertSha256 = "SHA256:x", protocol = AccountProtocol.SFTP)
        accounts.add(keyed, pem)

        val result = editingService.update(keyed, "127.0.0.1", server.port, "alice", "", null, useKey = false, label = "Key", pinnedFingerprint = "SHA256:x")

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(pem, accounts.passwordOf("k"))
    }
}
