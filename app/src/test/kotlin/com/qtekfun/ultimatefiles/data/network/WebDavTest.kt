package com.qtekfun.ultimatefiles.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.repository.DataStoreAccountRepository
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.RoutingFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.WebDavFileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.SecretCipher
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

private class FakeAccounts(account: WebDavAccount, private val password: String) : AccountRepository {
    val state = MutableStateFlow(listOf(account))
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String): String? = password
    override suspend fun add(account: WebDavAccount, password: String) { state.value += account }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
}

class WebDavTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: FakeNextcloud
    private lateinit var repo: WebDavFileSystemRepository
    private val root = "dav://a1/"

    private fun repository(password: String = "secret", chunkSize: Int = 1_000): WebDavFileSystemRepository {
        val account = WebDavAccount("a1", "My cloud", server.baseUrl, "alice", allowInsecureHttp = true) // the fake speaks plain HTTP
        return WebDavFileSystemRepository(
            FakeAccounts(account, password),
            WebDavClient(retryDelayMillis = 1),
            chunkSize,
        ) { null }
    }

    @Before
    fun setUp() {
        server = FakeNextcloud()
        repo = repository()
    }

    @After
    fun tearDown() = server.close()

    private val data4500 = ByteArray(4_500) { (it % 251).toByte() }

    @Test
    fun `the account shows up as a network volume`() = runTest {
        val volume = repo.volumes().single()
        assertEquals("My cloud", volume.label)
        assertEquals(root, volume.rootPath)
    }

    @Test
    fun `lists a folder with names, types, sizes and dates`() = runTest {
        server.dirs += "Videos"
        server.files["notes with space.txt"] = ByteArray(12)
        server.files["Videos/clip.mp4"] = ByteArray(5)

        val root = repo.listFiles(root).getOrThrow().associateBy { it.name }

        assertEquals(setOf("Videos", "notes with space.txt"), root.keys)
        assertTrue(root.getValue("Videos").isDirectory)
        assertEquals(12L, root.getValue("notes with space.txt").sizeBytes)
        assertTrue(root.getValue("notes with space.txt").lastModifiedMillis > 0)
        val inside = repo.listFiles("dav://a1/Videos").getOrThrow()
        assertEquals(listOf("dav://a1/Videos/clip.mp4"), inside.map { it.path })
    }

    @Test
    fun `parentOf walks up to the account root`() = runTest {
        assertEquals("dav://a1/a/b", repo.parentOf("dav://a1/a/b/c.txt"))
        assertEquals("dav://a1/", repo.parentOf("dav://a1/a"))
        assertNull(repo.parentOf("dav://a1/"))
    }

    @Test
    fun `a small upload is a single PUT and reads back identical`() = runTest {
        repo.openOutput(root, "small.bin", "application/octet-stream", overwrite = false).getOrThrow()
            .use { it.write(data4500, 0, 700) }

        assertArrayEquals(data4500.copyOf(700), server.files["small.bin"])
        assertTrue(server.log.none { it.contains("/uploads/") })
        val item = repo.stat("dav://a1/small.bin").getOrThrow()
        assertArrayEquals(data4500.copyOf(700), repo.openInput(item).getOrThrow().use { it.readBytes() })
    }

    @Test
    fun `a file of exactly one chunk is still a single PUT`() = runTest {
        repo.openOutput(root, "one.bin", "application/octet-stream", false).getOrThrow().use { it.write(ByteArray(1_000) { 3 }) }

        assertEquals(1_000, server.files.getValue("one.bin").size)
        assertTrue(server.log.none { it.contains("/uploads/") })
    }

    @Test
    fun `a bigger file goes up in numbered chunks and is assembled on the server`() = runTest {
        repo.openOutput(root, "big.bin", "application/octet-stream", false).getOrThrow().use { out ->
            // Odd write sizes to cross chunk boundaries in the middle of a write.
            var offset = 0
            while (offset < data4500.size) {
                val n = minOf(333, data4500.size - offset)
                out.write(data4500, offset, n)
                offset += n
            }
        }

        assertArrayEquals(data4500, server.files["big.bin"])
        val uploadRequests = server.log.filter { it.contains("/uploads/") }
        assertEquals(1, uploadRequests.count { it.startsWith("MKCOL") })
        assertEquals(5, uploadRequests.count { it.startsWith("PUT") }) // 4 full chunks + the 500-byte tail
        assertEquals(1, uploadRequests.count { it.startsWith("MOVE") && it.endsWith("/.file") })
    }

    @Test
    fun `a download that drops half way resumes with a Range request`() = runTest {
        val content = ByteArray(5_000) { (it * 7).toByte() }
        server.files["movie.bin"] = content
        server.dropNextGetAfterBytes = 1_500
        val item = repo.stat("dav://a1/movie.bin").getOrThrow()

        val downloaded = repo.openInput(item).getOrThrow().use { it.readBytes() }

        assertArrayEquals(content, downloaded)
        assertEquals(2, server.log.count { it.startsWith("GET") })
    }

    @Test
    fun `create, rename and delete`() = runTest {
        val folder = repo.createDirectory(root, "Docs").getOrThrow()
        val file = repo.createFile(folder.path, "a.txt", "text/plain").getOrThrow()
        val renamed = repo.rename(file, "b.txt").getOrThrow()

        assertEquals("dav://a1/Docs/b.txt", renamed.path)
        assertTrue("Docs/b.txt" in server.files)
        repo.delete(listOf(folder)).getOrThrow()
        assertFalse("Docs" in server.dirs)
        assertTrue(repo.listFiles(root).getOrThrow().isEmpty())
    }

    @Test
    fun `existing files are protected unless overwrite is requested`() = runTest {
        server.files["x.bin"] = ByteArray(3)

        assertTrue(repo.openOutput(root, "x.bin", "application/octet-stream", overwrite = false).isFailure)
        repo.openOutput(root, "x.bin", "application/octet-stream", overwrite = true).getOrThrow().use { it.write(ByteArray(9)) }
        assertEquals(9, server.files.getValue("x.bin").size)
    }

    @Test
    fun `wrong credentials are reported as 401 and not retried forever`() = runTest {
        val failure = repository(password = "wrong").listFiles(root).exceptionOrNull()

        assertTrue(failure is WebDavException)
        assertEquals(401, (failure as WebDavException).code)
        assertEquals(1, server.log.size)
    }

    @Test
    fun `the transfer engine copies a local tree to the server and back`() = runTest {
        val local = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val router = RoutingFileSystemRepository(local, local, repo, local, local)
        val engine = TransferEngine(router, FileStreamCopier(), bigFileBytes = 2_000)
        val source = tmp.newFolder("footage")
        File(source, "a.bin").writeBytes(data4500)
        File(source, "sub").mkdirs()
        File(source, "sub/b.txt").writeText("hello")
        val items = listOf(local.stat(source.path).getOrThrow())

        val up = engine.execute(TransferRequest(OperationType.COPY, items, root, verify = true)) { _, _ ->
            error("no conflict expected")
        }.toList()

        assertEquals(TransferStatus.COMPLETED, up.last().status)
        assertArrayEquals(data4500, server.files["footage/a.bin"])
        assertEquals("hello", String(server.files.getValue("footage/sub/b.txt")))
        assertTrue(server.files.keys.none { it.endsWith(".ultimatefiles-part") })

        val back = tmp.newFolder("back")
        val remote = repo.listFiles(root).getOrThrow()
        val down = engine.execute(TransferRequest(OperationType.CUT, remote, back.path)) { _, _ -> error("no conflict") }.toList()

        assertEquals(TransferStatus.COMPLETED, down.last().status)
        assertArrayEquals(data4500, File(back, "footage/a.bin").readBytes())
        assertTrue(server.files.isEmpty()) // a move deletes the source on the server
    }

    // --- pure helpers ---------------------------------------------------------------------------

    @Test
    fun `server addresses become DAV roots`() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice",
            WebDavAccountService.buildBaseUrl("https://cloud.example.com/", "alice"),
        )
        assertEquals(
            "https://example.com/nextcloud/remote.php/dav/files/alice%20b",
            WebDavAccountService.buildBaseUrl("https://example.com/nextcloud", "alice b"),
        )
        assertEquals(
            "https://dav.example.com/remote.php/webdav",
            WebDavAccountService.buildBaseUrl("https://dav.example.com/remote.php/webdav/", "alice"),
        )
        assertThrows(InvalidServerUrlException::class.java) { WebDavAccountService.buildBaseUrl("http://cloud.example.com", "a") }
        assertThrows(InvalidServerUrlException::class.java) { WebDavAccountService.buildBaseUrl("not a url", "a") }
    }

    @Test
    fun `session maps hrefs and finds the uploads endpoint`() {
        val session = WebDavSession("https://h.example/nc/remote.php/dav/files/alice%20b".toHttpUrl(), "alice b", "pw")

        assertEquals("Videos/my clip.mp4", session.relativeTo("/nc/remote.php/dav/files/alice%20b/Videos/my%20clip.mp4"))
        assertEquals("", session.relativeTo("https://h.example/nc/remote.php/dav/files/alice%20b/"))
        assertEquals("https://h.example/nc/remote.php/dav/uploads/alice%20b", session.uploadsUrl().toString())
        assertTrue(session.isNextcloud)
        assertNull(WebDavSession("https://h.example/webdav".toHttpUrl(), "u", "p").uploadsUrl())
    }

    @Test
    fun `multistatus parsing keeps only successful properties`() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
          <d:response><d:href>/remote.php/dav/files/alice/a%20b.txt</d:href>
            <d:propstat><d:prop><d:getcontentlength>42</d:getcontentlength><d:getcontenttype>text/plain</d:getcontenttype>
              <d:getlastmodified>Sat, 03 Oct 2026 10:00:00 GMT</d:getlastmodified><d:resourcetype/></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
            <d:propstat><d:prop><d:quota-used-bytes/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
          </d:response></d:multistatus>"""
        val session = WebDavSession("https://h.example/remote.php/dav/files/alice".toHttpUrl(), "alice", "pw")

        val entry = WebDavXml.parse(ByteArrayInputStream(xml.toByteArray()), session).single()

        assertEquals("a b.txt", entry.path)
        assertEquals(42L, entry.sizeBytes)
        assertEquals("text/plain", entry.contentType)
        assertFalse(entry.isDirectory)
        assertEquals(1_791_021_600_000L, entry.lastModifiedMillis)
    }

    // --- accounts ---------------------------------------------------------------------------------

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(token: String) = token.removePrefix("enc:").reversed()
    }

    @Test
    fun `accounts are stored with encrypted passwords and survive special characters`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = File(tmp.root, "a.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val accounts = DataStoreAccountRepository(store, ReversingCipher)
            val account = WebDavAccount("id1", "Home\tcloud", "https://h.example/remote.php/dav/files/me", "me", "ab".repeat(32), allowInsecureHttp = true)

            accounts.add(account, "p@ss\nword\\")

            assertEquals(listOf(account), accounts.accounts.first())
            assertEquals("p@ss\nword\\", accounts.passwordOf("id1"))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("p@ss")) // plain text never hits the disk

            accounts.remove("id1")
            assertTrue(accounts.accounts.first().isEmpty())
            assertNull(accounts.passwordOf("id1"))
        } finally {
            scope.cancel()
        }
    }
}
