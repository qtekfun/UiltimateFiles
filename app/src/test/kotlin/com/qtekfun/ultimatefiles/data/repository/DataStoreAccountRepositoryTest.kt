package com.qtekfun.ultimatefiles.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qtekfun.ultimatefiles.core.model.AccountProtocol
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.SecretCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreAccountRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var accounts: DataStoreAccountRepository

    private object Cipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(token: String) = token.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "a.preferences_pb") }
        accounts = DataStoreAccountRepository(store, Cipher)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `renaming changes only the label`() = runTest {
        val first = WebDavAccount("a", "Home", "smb://nas:445/share", "me", protocol = AccountProtocol.SMB)
        val second = WebDavAccount("b", "Work", "https://cloud.example/remote.php/dav/files/me", "me")
        accounts.add(first, "secret-a")
        accounts.add(second, "secret-b")

        accounts.rename("a", "NAS\tat home")

        val stored = accounts.accounts.first()
        assertEquals(listOf(first.copy(label = "NAS\tat home"), second), stored)
        assertEquals("secret-a", accounts.passwordOf("a"))
        assertEquals("secret-b", accounts.passwordOf("b"))
    }

    @Test
    fun `renaming an unknown account changes nothing`() = runTest {
        val only = WebDavAccount("a", "Home", "https://h.example/dav", "me")
        accounts.add(only, "pw")

        accounts.rename("missing", "x")

        assertEquals(listOf(only), accounts.accounts.first())
    }

    @Test
    fun `updating replaces the account in place, keeps the id and the order, and keeps the secret when none is given`() = runTest {
        val first = WebDavAccount("a", "Home", "smb://nas:445/share", "me", protocol = AccountProtocol.SMB)
        val second = WebDavAccount("b", "Work", "https://cloud.example/remote.php/dav/files/me", "me")
        accounts.add(first, "secret-a")
        accounts.add(second, "secret-b")

        accounts.update(first.copy(label = "NAS", baseUrl = "smb://other:445/media", username = "DOM\\me"), null)

        assertEquals(listOf(first.copy(label = "NAS", baseUrl = "smb://other:445/media", username = "DOM\\me"), second), accounts.accounts.first())
        assertEquals("secret-a", accounts.passwordOf("a"))
    }

    @Test
    fun `updating with a secret replaces it and an unknown id changes nothing`() = runTest {
        val only = WebDavAccount("a", "Home", "https://h.example/dav", "me")
        accounts.add(only, "old")

        accounts.update(only, "new")
        accounts.update(only.copy(id = "missing", label = "Ghost"), "x")

        assertEquals(listOf(only), accounts.accounts.first())
        assertEquals("new", accounts.passwordOf("a"))
        assertEquals(null, accounts.passwordOf("missing"))
    }
}
