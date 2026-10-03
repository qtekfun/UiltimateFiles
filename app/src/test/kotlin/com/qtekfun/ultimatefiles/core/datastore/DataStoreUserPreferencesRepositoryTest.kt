package com.qtekfun.ultimatefiles.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreUserPreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: DataStoreUserPreferencesRepository

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") }
        repo = DataStoreUserPreferencesRepository(store)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults`() = runTest {
        val prefs = repo.preferences.first()
        assertEquals(ViewMode.LIST, prefs.viewMode)
        assertEquals(SortOrder(SortField.NAME, true), prefs.sortOrder)
        assertNull(prefs.lastDirectoryPaths[PanelId.LEFT])
    }

    @Test
    fun `persists view mode sort order and last path per panel`() = runTest {
        repo.setViewMode(ViewMode.GRID)
        repo.setSortOrder(SortOrder(SortField.SIZE, ascending = false))
        repo.setLastDirectory(PanelId.RIGHT, "/storage/a")

        val prefs = repo.preferences.first()

        assertEquals(ViewMode.GRID, prefs.viewMode)
        assertEquals(SortOrder(SortField.SIZE, false), prefs.sortOrder)
        assertEquals("/storage/a", prefs.lastDirectoryPaths[PanelId.RIGHT])
        assertNull(prefs.lastDirectoryPaths[PanelId.LEFT])
    }
}
