package com.qtekfun.ultimatefiles.data.backup

import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.core.model.ViewMode
import com.qtekfun.ultimatefiles.core.model.WebDavAccount
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPrefs : UserPreferencesRepository {
    val state = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = state
    override suspend fun setViewMode(mode: ViewMode) { state.value = state.value.copy(viewMode = mode) }
    override suspend fun setSortOrder(order: SortOrder) { state.value = state.value.copy(sortOrder = order) }
    override suspend fun setLastDirectory(panel: PanelId, directoryPath: String) {
        state.value = state.value.copy(lastDirectoryPaths = state.value.lastDirectoryPaths + (panel to directoryPath))
    }
    override suspend fun setPanelIds(panels: List<PanelId>) { state.value = state.value.copy(panelIds = panels) }
    override suspend fun setPanelBarPosition(position: PanelBarPosition) { state.value = state.value.copy(panelBarPosition = position) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { state.value = state.value.copy(dynamicColor = enabled) }
    override suspend fun setVerifyCopies(enabled: Boolean) { state.value = state.value.copy(verifyCopies = enabled) }
    override suspend fun setThumbnailsOnNetwork(enabled: Boolean) { state.value = state.value.copy(thumbnailsOnNetwork = enabled) }
}

private class MemoryAccounts : AccountRepository {
    private val state = MutableStateFlow(emptyList<WebDavAccount>())
    val passwords = mutableMapOf<String, String>()
    override val accounts: Flow<List<WebDavAccount>> = state
    override suspend fun passwordOf(accountId: String): String? = passwords[accountId]
    override suspend fun add(account: WebDavAccount, password: String) {
        state.value = state.value.filterNot { it.id == account.id } + account
        passwords[account.id] = password
    }
    override suspend fun remove(accountId: String) { state.value = state.value.filterNot { it.id == accountId } }
    override suspend fun rename(accountId: String, label: String) { state.value = state.value.map { if (it.id == accountId) it.copy(label = label) else it } }
}

class BackupManagerTest {
    private val sourcePrefs = MemoryPrefs()
    private val sourceAccounts = MemoryAccounts()
    private val source = BackupManager(sourcePrefs, sourceAccounts, kdfIterations = 1_000)

    private val account = WebDavAccount("a1", "Home", "https://cloud.example.com/remote.php/dav/files/alice", "alice", "cd".repeat(32), allowInsecureHttp = true)

    private fun target(): Triple<MemoryPrefs, MemoryAccounts, BackupManager> {
        val prefs = MemoryPrefs()
        val accounts = MemoryAccounts()
        return Triple(prefs, accounts, BackupManager(prefs, accounts, kdfIterations = 1_000))
    }

    private fun problemOf(block: suspend () -> Unit): BackupProblem? = try {
        runBlocking { block() }
        null
    } catch (e: BackupException) {
        e.problem
    }

    private fun seed() = runBlocking {
        sourcePrefs.setThemeMode(ThemeMode.AMOLED)
        sourcePrefs.setDynamicColor(false)
        sourcePrefs.setVerifyCopies(true)
        sourcePrefs.setViewMode(ViewMode.GRID)
        sourcePrefs.setSortOrder(SortOrder(SortField.SIZE, ascending = false))
        sourcePrefs.setLastDirectory(PanelId.LEFT, "/storage/emulated/0/DCIM")
        sourceAccounts.add(account, "app-pass-123")
    }

    @Test fun `settings and accounts survive a round trip`() = runBlocking {
        seed()
        val file = source.export(includeAccounts = true, passphrase = "correct horse")
        val (prefs, accounts, manager) = target()

        val summary = manager.import(file, "correct horse")

        assertTrue(summary.settingsApplied)
        assertEquals(1, summary.accountsAdded)
        val restored = prefs.state.value
        assertEquals(ThemeMode.AMOLED, restored.themeMode)
        assertFalse(restored.dynamicColor)
        assertTrue(restored.verifyCopies)
        assertEquals(ViewMode.GRID, restored.viewMode)
        assertEquals(SortOrder(SortField.SIZE, false), restored.sortOrder)
        assertTrue("device specific folders are not exported", restored.lastDirectoryPaths.isEmpty())
        assertEquals(account, accounts.accounts.first().single())
        assertEquals("app-pass-123", accounts.passwordOf("a1"))
    }

    @Test fun `the file never contains the app password in clear`() = runBlocking {
        seed()
        val file = source.export(true, "correct horse")
        assertFalse(file.contains("app-pass-123"))
        assertFalse(file.contains("correct horse"))
        assertFalse(file.contains("cloud.example.com")) // the whole accounts block is encrypted, not just passwords
    }

    @Test fun `wrong passphrase changes nothing`() = runBlocking {
        seed()
        val file = source.export(true, "correct horse")
        val (prefs, accounts, manager) = target()

        assertEquals(BackupProblem.WRONG_PASSPHRASE, problemOf { manager.import(file, "battery staple") })

        assertEquals(UserPreferences(), prefs.state.value)
        assertTrue(accounts.accounts.first().isEmpty())
    }

    @Test fun `a backup with accounts needs a passphrase to import`() = runBlocking {
        seed()
        val file = source.export(true, "correct horse")
        assertEquals(BackupProblem.NEEDS_PASSPHRASE, problemOf { target().third.import(file, null) })
    }

    @Test fun `exporting accounts without a passphrase is refused`() = runBlocking {
        seed()
        assertEquals(BackupProblem.NEEDS_PASSPHRASE, problemOf { source.export(true, "") })
    }

    @Test fun `settings only backup needs no passphrase and leaves accounts alone`() = runBlocking {
        seed()
        val file = source.export(includeAccounts = false, passphrase = null)
        val (prefs, accounts, manager) = target()
        accounts.add(WebDavAccount("keep", "Keep", "https://x.example/dav", "bob"), "pw")

        val summary = manager.import(file, null)

        assertEquals(0, summary.accountsAdded)
        assertEquals(ThemeMode.AMOLED, prefs.state.value.themeMode)
        assertEquals(listOf("keep"), accounts.accounts.first().map { it.id })
    }

    @Test fun `importing twice does not duplicate accounts`() = runBlocking {
        seed()
        val file = source.export(true, "correct horse")
        val (_, accounts, manager) = target()
        manager.import(file, "correct horse")
        manager.import(file, "correct horse")
        assertEquals(1, accounts.accounts.first().size)
    }

    @Test fun `garbage and foreign json are rejected`() {
        val manager = target().third
        assertEquals(BackupProblem.INVALID_FILE, problemOf { manager.import("not json", null) })
        assertEquals(BackupProblem.INVALID_FILE, problemOf { manager.import("""{"hello":1}""", null) })
        assertEquals(
            BackupProblem.UNSUPPORTED_VERSION,
            problemOf { manager.import("""{"format":"UltimateFiles backup","version":99}""", null) },
        )
    }

    @Test fun `unknown enum values are ignored instead of failing`() = runBlocking {
        val (prefs, _, manager) = target()
        manager.import(
            """{"format":"UltimateFiles backup","version":1,"settings":{"themeMode":"NEON","verifyCopies":true}}""",
            null,
        )
        assertEquals(ThemeMode.SYSTEM, prefs.state.value.themeMode)
        assertTrue(prefs.state.value.verifyCopies)
    }

    @Test fun `no accounts means no accounts block`() = runBlocking {
        val file = source.export(true, null)
        assertNull(org.json.JSONObject(file).optJSONObject("accounts"))
    }
}
