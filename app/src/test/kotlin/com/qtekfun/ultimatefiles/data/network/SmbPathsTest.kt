package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.data.repository.SmbFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** The only SMB parts that can be checked without a server: how addresses, logins and paths are written and read. */
class SmbPathsTest {
    @Test
    fun `parses the stored address`() {
        assertEquals(Triple("nas.local", 445, "media"), SmbConnector.parse("smb://nas.local:445/media"))
        assertEquals(Triple("10.0.0.5", 1445, "docs"), SmbConnector.parse("smb://10.0.0.5:1445/docs"))
        assertEquals(Triple("nas", 445, "share"), SmbConnector.parse("smb://nas/share"))
    }

    @Test
    fun `splits the domain from the user`() {
        assertEquals("CORP" to "alice", SmbConnector.splitLogin("CORP\\alice"))
        assertEquals("" to "alice", SmbConnector.splitLogin("alice"))
    }

    @Test
    fun `paths use slashes in the app and backslashes on the wire`() {
        assertEquals("a\\b\\c.txt", SmbFileSystemRepository.smbPath("a/b/c.txt"))
        assertEquals("", SmbFileSystemRepository.smbPath(""))
        assertEquals("acc" to "a/b", SmbFileSystemRepository.split("smb://acc/a/b/"))
        assertEquals("acc" to "", SmbFileSystemRepository.split(SmbFileSystemRepository.rootOf("acc")))
    }
}
