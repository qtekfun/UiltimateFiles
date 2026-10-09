package com.qtekfun.ultimatefiles.ui.browser

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.core.model.ViewMode
import com.qtekfun.ultimatefiles.data.io.FileStreamCopier
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.domain.clipboard.ClipboardManager
import com.qtekfun.ultimatefiles.domain.history.HistoryEntry
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRecorder
import com.qtekfun.ultimatefiles.domain.history.TransferHistoryRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import com.qtekfun.ultimatefiles.domain.transfer.TransferCoordinator
import com.qtekfun.ultimatefiles.domain.usecase.BatchCopyUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BatchMoveUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BuildBreadcrumbUseCase
import com.qtekfun.ultimatefiles.domain.usecase.DeleteUseCase
import com.qtekfun.ultimatefiles.domain.usecase.HashCalcUseCase
import com.qtekfun.ultimatefiles.domain.usecase.TransferEngine
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakePreferences : UserPreferencesRepository {
        val flow = MutableStateFlow(UserPreferences())
        override val preferences = flow
        override suspend fun setViewMode(mode: ViewMode) = flow.update { it.copy(viewMode = mode) }
        override suspend fun setSortOrder(order: SortOrder) = flow.update { it.copy(sortOrder = order) }
        override suspend fun setLastDirectory(panel: PanelId, directoryPath: String) =
            flow.update { it.copy(lastDirectoryPaths = it.lastDirectoryPaths + (panel to directoryPath)) }

        override suspend fun setPanelIds(panels: List<PanelId>) = flow.update { it.copy(panelIds = panels) }

        override suspend fun setPanelBarPosition(position: PanelBarPosition) = flow.update { it.copy(panelBarPosition = position) }

        override suspend fun setThemeMode(mode: ThemeMode) = flow.update { it.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) = flow.update { it.copy(dynamicColor = enabled) }
        override suspend fun setVerifyCopies(enabled: Boolean) = flow.update { it.copy(verifyCopies = enabled) }
        override suspend fun setThumbnailsOnNetwork(enabled: Boolean) = flow.update { it.copy(thumbnailsOnNetwork = enabled) }
    }

    private class FakeHistory : TransferHistoryRepository {
        val added = mutableListOf<HistoryEntry>()
        override val entries: Flow<List<HistoryEntry>> = MutableStateFlow(emptyList())
        override suspend fun add(entry: HistoryEntry) {
            added += entry
        }
        override suspend fun clear() = added.clear()
    }

    private val prefs = FakePreferences()
    private val clipboard = ClipboardManager()
    private val history = FakeHistory()
    private val volumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tmp.newFolder("zeta")
        tmp.newFolder("alpha")
        tmp.newFile("b.txt").writeText("hello")
        repo = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val recorder = TransferHistoryRecorder(history, repo)
        coordinator = TransferCoordinator(TransferEngine(repo, FileStreamCopier()), recorder) { }
        this.recorder = recorder
        viewModel = newViewModel()
    }

    private lateinit var repo: LocalFileSystemRepository
    private lateinit var coordinator: TransferCoordinator
    private lateinit var recorder: TransferHistoryRecorder

    private fun newViewModel(
        classifier: com.qtekfun.ultimatefiles.domain.connection.ConnectionFailureClassifier =
            com.qtekfun.ultimatefiles.domain.connection.ConnectionFailureClassifier.None,
        health: com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth = com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth(),
        accountLabelOf: suspend (String) -> String? = { null },
        repository: com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository = repo,
    ) = BrowserViewModel(
        panel = PanelId.LEFT,
        repository = repository,
        preferences = prefs,
        clipboardManager = clipboard,
        coordinator = coordinator,
        buildBreadcrumb = BuildBreadcrumbUseCase(repository),
        deleteFiles = DeleteUseCase(repo, recorder),
        calculateHash = HashCalcUseCase(repo),
        copyFiles = BatchCopyUseCase(coordinator, prefs),
        moveFiles = BatchMoveUseCase(coordinator, prefs),
        dragDrop = DragDropState(),
        volumeChanges = object : VolumeChangeSource {
            override val changes: Flow<Unit> = volumeEvents
        },
        classifier = classifier,
        health = health,
        accountLabelOf = accountLabelOf,
    )

    @After
    fun tearDown() {
        // The view model's coroutines resume on IO threads; one still running after resetMain() would fail the next test.
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun loaded(): BrowserState = viewModel.state.first { !it.isLoading && it.currentPath != null }

    @Test
    fun `opens the volume root with folders first`() = runTest {
        val state = loaded()

        assertEquals(tmp.root.path, state.currentPath)
        assertEquals(listOf("alpha", "zeta", "b.txt"), state.items.map { it.name })
        assertNull(state.parentPath)
        assertEquals(listOf("Internal"), state.breadcrumb.map { it.label })
    }

    @Test
    fun `navigating into a folder and back up`() = runTest {
        val root = loaded()
        val alpha = root.items.first { it.name == "alpha" }

        viewModel.onEvent(BrowserEvent.OpenItem(alpha))
        val inside = viewModel.state.first { it.currentPath == alpha.path && !it.isLoading }
        assertEquals(tmp.root.path, inside.parentPath)
        assertEquals(listOf("Internal", "alpha"), inside.breadcrumb.map { it.label })

        viewModel.onEvent(BrowserEvent.NavigateUp)
        assertEquals(tmp.root.path, viewModel.state.first { it.currentPath == tmp.root.path && !it.isLoading }.currentPath)
    }

    @Test
    fun `revealing an item opens its folder and selects it`() = runTest {
        loaded()
        val inner = File(tmp.root, "alpha/inner.txt").apply { writeText("x") }
        val item = FileItem(inner.path, "inner.txt", isDirectory = false, sizeBytes = 1, lastModifiedMillis = 0, mimeType = null)

        viewModel.onEvent(BrowserEvent.Reveal(item))

        val shown = viewModel.state.first { it.currentPath == File(tmp.root, "alpha").path && !it.isLoading && it.selectedPaths.isNotEmpty() }
        assertEquals(setOf(inner.path), shown.selectedPaths)
    }

    @Test
    fun `selection can be toggled, extended to all and cleared`() = runTest {
        val items = loaded().items

        viewModel.onEvent(BrowserEvent.ToggleSelection(items.first()))
        assertEquals(1, viewModel.state.value.selectedItems.size)
        assertTrue(viewModel.state.value.isSelecting)

        viewModel.onEvent(BrowserEvent.SelectAll)
        assertEquals(items.size, viewModel.state.value.selectedItems.size)

        viewModel.onEvent(BrowserEvent.ClearSelection)
        assertFalse(viewModel.state.value.isSelecting)
    }

    @Test
    fun `search filters the visible items by name`() = runTest {
        loaded()

        viewModel.onEvent(BrowserEvent.ToggleSearch)
        viewModel.onEvent(BrowserEvent.SetSearchQuery("ZET"))

        assertEquals(listOf("zeta"), viewModel.state.value.visibleItems.map { it.name })
    }

    @Test
    fun `sorting twice by the same field flips the direction`() = runTest {
        loaded()

        viewModel.onEvent(BrowserEvent.SortBy(SortField.SIZE))
        assertEquals(SortOrder(SortField.SIZE, true), viewModel.state.first { it.sortOrder.field == SortField.SIZE }.sortOrder)

        viewModel.onEvent(BrowserEvent.SortBy(SortField.SIZE))
        assertFalse(viewModel.state.first { it.sortOrder.field == SortField.SIZE && !it.sortOrder.ascending }.sortOrder.ascending)
    }

    @Test
    fun `the view mode toggles between list and grid and is shared through the preferences`() = runTest {
        assertEquals(ViewMode.LIST, loaded().viewMode)

        viewModel.onEvent(BrowserEvent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals("preference after the first toggle", ViewMode.GRID, prefs.flow.value.viewMode)
        assertEquals("panel state after the first toggle", ViewMode.GRID, viewModel.state.value.viewMode)

        viewModel.onEvent(BrowserEvent.ToggleViewMode)
        advanceUntilIdle()
        assertEquals("preference after the second toggle", ViewMode.LIST, prefs.flow.value.viewMode)
        assertEquals("panel state after the second toggle", ViewMode.LIST, viewModel.state.value.viewMode)
    }

    @Test
    fun `new folder dialog creates the folder`() = runTest {
        loaded()

        viewModel.onEvent(BrowserEvent.RequestNewFolder)
        assertEquals(BrowserDialog.NewFolder, viewModel.state.value.dialog)
        viewModel.onEvent(BrowserEvent.ConfirmName("created"))

        val state = viewModel.state.first { s -> s.items.any { it.name == "created" } }
        assertNull(state.dialog)
        assertTrue(tmp.root.resolve("created").isDirectory)
    }

    @Test
    fun `delete asks for confirmation then removes the file`() = runTest {
        val file = loaded().items.first { it.name == "b.txt" }

        viewModel.onEvent(BrowserEvent.RequestDelete(listOf(file)))
        assertTrue(viewModel.state.value.dialog is BrowserDialog.ConfirmDelete)
        assertTrue(tmp.root.resolve("b.txt").exists())

        viewModel.onEvent(BrowserEvent.ConfirmDelete)
        viewModel.state.first { s -> s.items.none { it.name == "b.txt" } }
        assertFalse(tmp.root.resolve("b.txt").exists())
    }

    @Test
    fun `copy and cut fill the shared clipboard and cancel empties it`() = runTest {
        val file = loaded().items.first { it.name == "b.txt" }

        viewModel.onEvent(BrowserEvent.Copy(listOf(file)))
        assertEquals(OperationType.COPY, clipboard.state.value?.operation)

        viewModel.onEvent(BrowserEvent.Cut(listOf(file)))
        assertEquals(OperationType.CUT, clipboard.state.value?.operation)
        assertEquals(tmp.root.path, clipboard.state.value?.sourcePath)

        viewModel.onEvent(BrowserEvent.CancelClipboard)
        assertNull(clipboard.state.value)
    }

    @Test
    fun `properties dialog computes the hashes`() = runTest {
        val file = loaded().items.first { it.name == "b.txt" }

        viewModel.onEvent(BrowserEvent.ShowProperties(file))
        viewModel.onEvent(BrowserEvent.ComputeHash)

        val dialog = viewModel.state.first { (it.dialog as? BrowserDialog.Properties)?.hash is com.qtekfun.ultimatefiles.core.model.HashState.Done }
            .dialog as BrowserDialog.Properties
        val done = dialog.hash as com.qtekfun.ultimatefiles.core.model.HashState.Done
        assertEquals("5d41402abc4b2a76b9719d911017c592", done.hashes.md5)
    }

    @Test
    fun `delete is recorded in the history`() = runTest {
        val file = loaded().items.first { it.name == "b.txt" }

        viewModel.onEvent(BrowserEvent.RequestDelete(listOf(file)))
        viewModel.onEvent(BrowserEvent.ConfirmDelete)
        viewModel.state.first { s -> s.items.none { it.name == "b.txt" } }

        assertEquals(1, history.added.size)
        assertEquals("b.txt", history.added.first().firstItemName)
    }

    @Test
    fun `panel falls back to an existing folder when its folder disappears`() = runTest {
        val alpha = loaded().items.first { it.name == "alpha" }
        viewModel.onEvent(BrowserEvent.OpenItem(alpha))
        viewModel.state.first { it.currentPath == alpha.path && !it.isLoading }

        // Simulates an ejected drive / folder removed from outside the app.
        tmp.root.resolve("alpha").deleteRecursively()
        volumeEvents.tryEmit(Unit)

        val state = viewModel.state.first { it.currentPath == tmp.root.path && !it.isLoading }
        assertEquals(listOf("Internal"), state.breadcrumb.map { it.label })
    }

    /** Shows the folder of this device under the path of a server account, as if `dav://nas/` held the same files. */
    private inner class AsIfRemote(private val inner: com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository) :
        com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository by inner {
        private fun remote(path: String) = path.startsWith("dav://nas/")
        override suspend fun listFiles(uriOrPath: String) = inner.listFiles(if (remote(uriOrPath)) tmp.root.path else uriOrPath)
        override suspend fun stat(uriOrPath: String) =
            if (remote(uriOrPath)) inner.stat(tmp.root.path).map { it.copy(path = uriOrPath) } else inner.stat(uriOrPath)
        override suspend fun parentOf(uriOrPath: String) = if (remote(uriOrPath)) null else inner.parentOf(uriOrPath)
    }

    private val timeoutClassifier = com.qtekfun.ultimatefiles.domain.connection.ConnectionFailureClassifier {
        com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NO_RESPONSE
    }

    private suspend fun effectsAfter(vm: BrowserViewModel, action: () -> Unit): List<BrowserEffect> {
        val seen = java.util.Collections.synchronizedList(mutableListOf<BrowserEffect>())
        val collector = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { vm.effects.collect { seen += it } }
        vm.state.first { !it.isLoading && it.currentPath != null }
        action()
        // The listing runs on an IO thread; give it real time to fail and to be reported.
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(600) }
        collector.cancel()
        return seen.toList()
    }

    @Test
    fun `a server folder that cannot be opened is announced with its reason and marks the account`() = runTest {
        val health = com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth()
        val vm = newViewModel(classifier = timeoutClassifier, health = health, accountLabelOf = { "Casa NAS" })
        try {
            val seen = effectsAfter(vm) { vm.onEvent(BrowserEvent.Navigate("dav://nas/Photos")) }

            val notice = seen.filterIsInstance<BrowserEffect.ConnectionNotice>().single()
            assertEquals(com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NO_RESPONSE, notice.problem.kind)
            assertEquals("nas", notice.problem.accountId)
            assertEquals("Casa NAS", notice.accountLabel)
            assertEquals("dav://nas/Photos", notice.retryPath)
            assertEquals(com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NO_RESPONSE, health.problems.value["nas"]?.kind)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a folder of this device that cannot be opened is not a connection problem`() = runTest {
        val health = com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth()
        val vm = newViewModel(classifier = timeoutClassifier, health = health)
        try {
            val seen = effectsAfter(vm) { vm.onEvent(BrowserEvent.Navigate(File(tmp.root, "does-not-exist").path)) }

            assertTrue(seen.none { it is BrowserEffect.ConnectionNotice })
            assertTrue(health.problems.value.isEmpty())
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a missing folder inside an account is not blamed on the account, a missing top level is`() = runTest {
        val notFound = com.qtekfun.ultimatefiles.domain.connection.ConnectionFailureClassifier {
            com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NOT_FOUND
        }
        val health = com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth()
        val vm = newViewModel(classifier = notFound, health = health)
        try {
            val inside = effectsAfter(vm) { vm.onEvent(BrowserEvent.Navigate("dav://nas/gone/folder")) }
            assertTrue(inside.none { it is BrowserEffect.ConnectionNotice })
            assertTrue(health.problems.value.isEmpty())

            val top = effectsAfter(vm) { vm.onEvent(BrowserEvent.Navigate("dav://nas/")) }
            assertEquals(1, top.filterIsInstance<BrowserEffect.ConnectionNotice>().size)
            assertEquals(com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NOT_FOUND, health.problems.value["nas"]?.kind)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a later successful listing clears the mark of the account`() = runTest {
        val health = com.qtekfun.ultimatefiles.domain.connection.ConnectionHealth()
        health.report(com.qtekfun.ultimatefiles.core.model.ConnectionProblem(com.qtekfun.ultimatefiles.core.model.ConnectionProblemKind.NO_RESPONSE, "nas"))
        val vm = newViewModel(classifier = timeoutClassifier, health = health, repository = AsIfRemote(repo))
        try {
            val seen = effectsAfter(vm) { vm.onEvent(BrowserEvent.Navigate("dav://nas/")) }

            assertTrue(seen.none { it is BrowserEffect.ConnectionNotice })
            assertTrue("the account works again", health.problems.value.isEmpty())
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}
