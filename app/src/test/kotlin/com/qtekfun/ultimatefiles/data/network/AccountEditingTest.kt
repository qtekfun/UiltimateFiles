package com.qtekfun.ultimatefiles.data.network

import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEditingTest {
    private val cloud = WebDavAccount(
        "n", "Cloud", "https://cloud.example.com/remote.php/dav/files/alice", "alice",
        pinnedCertSha256 = "ab".repeat(32), allowInsecureHttp = false,
    )
    private val sftp = WebDavAccount("s", "Box", "sftp://box.lan:2222", "bob", pinnedCertSha256 = "SHA256:abc", protocol = AccountProtocol.SFTP)
    private val smb = WebDavAccount("m", "NAS", "smb://nas.lan:445/media", "HOME\\carol", protocol = AccountProtocol.SMB)

    @Test
    fun `the form of each protocol shows what was typed when the account was created`() {
        assertEquals(AccountFormValues(AccountProtocol.WEBDAV, "Cloud", server = "https://cloud.example.com", username = "alice"), AccountEditing.formOf(cloud))
        assertEquals(AccountFormValues(AccountProtocol.SFTP, "Box", host = "box.lan", port = 2222, username = "bob"), AccountEditing.formOf(sftp))
        assertEquals(
            AccountFormValues(AccountProtocol.SMB, "NAS", host = "nas.lan", port = 445, share = "media", domain = "HOME", username = "carol"),
            AccountEditing.formOf(smb),
        )
    }

    @Test
    fun `a smb login without a domain and an sftp host without a port are read back`() {
        assertEquals("", AccountEditing.formOf(smb.copy(username = "carol")).domain)
        assertEquals(22, AccountEditing.formOf(sftp.copy(baseUrl = "sftp://box.lan")).port)
    }

    @Test
    fun `the server root is found also in a sub-folder and for other addresses`() {
        assertEquals("https://h.example/nextcloud", AccountEditing.serverRootOf("https://h.example/nextcloud/remote.php/dav/files/u"))
        assertEquals("https://h.example/dav/u", AccountEditing.serverRootOf("https://h.example/dav/u/"))
    }

    @Test
    fun `the same server is recognised whatever the case or the trailing slash`() {
        assertTrue(AccountEditing.sameServer(cloud.baseUrl, "https://Cloud.Example.com/"))
        assertFalse(AccountEditing.sameServer(cloud.baseUrl, "https://other.example.com"))
        assertFalse(AccountEditing.sameServer(cloud.baseUrl, "http://cloud.example.com"))
    }

    @Test
    fun `certificate trust only carries over to the same server`() {
        assertEquals(TrustChoice("ab".repeat(32), false), AccountEditing.trustFor(cloud, "https://cloud.example.com"))
        assertEquals(TrustChoice(), AccountEditing.trustFor(cloud, "https://other.example.com"))
    }

    @Test
    fun `the pinned host key only carries over to the same host and port`() {
        assertEquals("SHA256:abc", AccountEditing.sftpPinFor(sftp, "BOX.lan", 2222))
        assertNull(AccountEditing.sftpPinFor(sftp, "box.lan", 22))
        assertNull(AccountEditing.sftpPinFor(sftp, "other.lan", 2222))
    }

    private val key = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"

    @Test
    fun `an empty password keeps the stored one`() {
        assertNull(AccountEditing.sftpSecret("old", "", null, useKey = false))
        assertEquals("new", AccountEditing.sftpSecret("old", "new", null, useKey = false))
    }

    @Test
    fun `a picked key replaces the stored secret and its passphrase is what was typed`() {
        assertEquals(key, AccountEditing.sftpSecret("old", "", key, useKey = true))
        assertEquals(key + "\u0000" + "pass", AccountEditing.sftpSecret("old", "pass", key, useKey = true))
    }

    @Test
    fun `the stored key stays and only its passphrase changes when one is typed`() {
        val stored = key + "\u0000" + "old"
        assertNull(AccountEditing.sftpSecret(stored, "", null, useKey = true))
        assertEquals(key + "\u0000" + "new", AccountEditing.sftpSecret(stored, "new", null, useKey = true))
    }

    @Test
    fun `leaving a key for a password needs the password`() {
        assertThrows(IllegalArgumentException::class.java) { AccountEditing.sftpSecret(key, "", null, useKey = false) }
        assertEquals("pw", AccountEditing.sftpSecret(key, "pw", null, useKey = false))
    }
}
