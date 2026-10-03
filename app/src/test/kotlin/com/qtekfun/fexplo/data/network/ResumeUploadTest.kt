package com.qtekfun.fexplo.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResumeUploadTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: FakeNextcloud
    private lateinit var store: FileUploadResumeStore
    private val client = WebDavClient(retryDelayMillis = 1)
    private val chunk = 1_000
    private val key = "dest-key"

    @Before fun setUp() {
        server = FakeNextcloud()
        store = FileUploadResumeStore(File(tmp.root, "resume.tsv"))
    }

    @After fun tearDown() = server.close()

    private fun session() = WebDavSession(server.baseUrl.toHttpUrl(), "alice", "secret")

    private fun stream() = WebDavUploadStream(client, session(), "video.part", "video/mp4", overwrite = true, chunkSize = chunk, resume = store, resumeKey = key)

    private fun chunkPuts(from: Int) = synchronized(server.log) { server.log.drop(from).count { it.startsWith("PUT /uploads/") } }

    private fun data(size: Int, seed: Int = 0) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    @Test fun `an upload cut short by the app dying continues where it stopped`() {
        val bytes = data(3_500)
        // First run: two full chunks are on the server, the rest was only in memory when the process died.
        stream().apply { write(bytes, 0, 2_500) } // never closed
        assertNotNull(store.load(key))
        assertEquals(2, store.load(key)!!.chunkHashes.size)

        val before = synchronized(server.log) { server.log.size }
        stream().use { it.write(bytes) }

        assertArrayEquals(bytes, server.files["video.part"])
        assertEquals("only chunks 3 and 4 are uploaded again", 2, chunkPuts(before))
        assertNull("finished uploads are forgotten", store.load(key))
    }

    @Test fun `a chunk that changed is uploaded again and stale server chunks are dropped`() {
        val original = data(3_500)
        stream().apply { write(original, 0, 3_500) } // chunks 1..3 on the server, never finished

        // The source now has the same first chunk, then different, shorter content.
        val changed = original.copyOf(1_000) + data(500, seed = 7)
        stream().use { it.write(changed) }

        assertArrayEquals(changed, server.files["video.part"])
    }

    @Test fun `a server that forgot the upload gets a fresh one`() {
        val bytes = data(2_600)
        stream().apply { write(bytes, 0, 2_100) }
        server.dropPendingUploads()

        stream().use { it.write(bytes) }

        assertArrayEquals(bytes, server.files["video.part"])
    }

    @Test fun `small files never touch the resume store`() {
        stream().use { it.write(data(300)) }
        assertNull(store.load(key))
        assertEquals(300, server.files["video.part"]!!.size)
    }

    @Test fun `the store round trips and ignores old entries`() {
        var now = 1_000_000L
        val s = FileUploadResumeStore(File(tmp.root, "other.tsv"), { now }, maxAgeMillis = 10_000)
        s.save("a", UploadRecord("id-a", 1000, listOf("h1", "h2"), now))
        s.save("b", UploadRecord("id-b", 2000, emptyList(), now))
        assertEquals(UploadRecord("id-a", 1000, listOf("h1", "h2"), now), s.load("a"))
        assertEquals(emptyList<String>(), s.load("b")!!.chunkHashes)
        s.clear("a")
        assertNull(s.load("a"))
        now += 20_000
        assertNull("expired", s.load("b"))
        assertTrue(File(tmp.root, "other.tsv").exists())
    }
}
