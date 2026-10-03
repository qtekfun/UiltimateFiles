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
}

/** Runs the real SFTP code against an embedded Apache MINA SSH server whose root is a temporary folder. */
class SftpTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: SshServer
    private lateinit var root: File
    private val accounts = MemoryAccounts()
    private val connector = SshConnector.forJvm()

    @Before
    fun setUp() {
        root = tmp.newFolder("server-root")
        server = SshServer.setUpDefaultServer().apply {
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(tmp.newFile("hostkey.ser").toPath())
            setPasswordAuthenticator { user, password, _ -> user == "alice" && password == "secret" }
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
}
