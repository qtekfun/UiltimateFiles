package com.qtekfun.ultimatefiles.core.util

import com.qtekfun.ultimatefiles.core.model.ConnectionProblemCodec
import com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAccountsTest {
    @Test
    fun `only server paths belong to an account`() {
        assertEquals("nas", RemoteAccounts.idOf("dav://nas/Photos/a.jpg"))
        assertEquals("pi-1", RemoteAccounts.idOf("sftp://pi-1/"))
        assertEquals("share", RemoteAccounts.idOf("smb://share"))
        assertNull(RemoteAccounts.idOf("/storage/emulated/0/Download"))
        assertNull(RemoteAccounts.idOf("content://com.android.externalstorage.documents/tree/1234%3A"))
        assertNull(RemoteAccounts.idOf("archive://%2Fsdcard%2Fa.zip!/inner"))
        assertNull(RemoteAccounts.idOf("dav:///x"))
    }

    @Test
    fun `the top of an account is told apart from a folder inside it`() {
        assertTrue(RemoteAccounts.isRoot("dav://nas/"))
        assertTrue(RemoteAccounts.isRoot("sftp://pi"))
        assertFalse(RemoteAccounts.isRoot("dav://nas/Photos"))
        assertFalse(RemoteAccounts.isRoot("/storage/emulated/0"))
    }

    @Test
    fun `drawer volumes map back to their account`() {
        assertEquals("nas", RemoteAccounts.idOfVolume("dav:nas"))
        assertEquals("pi", RemoteAccounts.idOfVolume("sftp:pi"))
        assertEquals("srv", RemoteAccounts.idOfVolume("smb:srv"))
        assertNull(RemoteAccounts.idOfVolume("internal"))
        assertNull(RemoteAccounts.idOfVolume("removable:1234-ABCD"))
    }

    @Test
    fun `a connection problem survives the history log, and plain errors are left alone`() {
        val encoded = ConnectionProblemCodec.encode(ConnectionProblemKind.NO_RESPONSE, "Casa: NAS")
        assertEquals(ConnectionProblemKind.NO_RESPONSE to "Casa: NAS", ConnectionProblemCodec.decode(encoded))
        assertEquals(ConnectionProblemKind.AUTH to null, ConnectionProblemCodec.decode(ConnectionProblemCodec.encode(ConnectionProblemKind.AUTH, null)))
        assertNull(ConnectionProblemCodec.decode("Cannot write file"))
        assertNull(ConnectionProblemCodec.decode("connection:BOGUS:x"))
        assertNull(ConnectionProblemCodec.decode(null))
    }
}
