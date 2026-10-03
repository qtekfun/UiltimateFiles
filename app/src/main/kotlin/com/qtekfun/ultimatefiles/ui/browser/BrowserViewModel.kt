package com.qtekfun.ultimatefiles.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.HashState
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferRequest
import com.qtekfun.ultimatefiles.domain.usecase.ViewerKind
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.model.ViewMode
import com.qtekfun.ultimatefiles.core.util.MimeTypes
import com.qtekfun.ultimatefiles.core.util.sortedByOrder
import com.qtekfun.ultimatefiles.domain.clipboard.ClipboardManager
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import com.qtekfun.ultimatefiles.domain.transfer.TransferCoordinator
import com.qtekfun.ultimatefiles.domain.usecase.BatchCopyUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BatchMoveUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BuildBreadcrumbUseCase
import com.qtekfun.ultimatefiles.domain.usecase.DeleteUseCase
import com.qtekfun.ultimatefiles.domain.usecase.HashCalcUseCase
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import com.qtekfun.ultimatefiles.ui.dualpanel.DragPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State holder of one file panel; the two panels are two independent instances. */
class BrowserViewModel(
    private val panel: PanelId,
    private val repository: FileSystemRepository,
    private val preferences: UserPreferencesRepository,
    private val clipboardManager: ClipboardManager,
    private val coordinator: TransferCoordinator,
    private val buildBreadcrumb: BuildBreadcrumbUseCase,
    private val deleteFiles: DeleteUseCase,
    private val calculateHash: HashCalcUseCase,
    private val copyFiles: BatchCopyUseCase,
    private val moveFiles: BatchMoveUseCase,
    private val dragDrop: DragDropState,
    private val volumeChanges: VolumeChangeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _effects = Channel<BrowserEffect>(Channel.BUFFERED)
    val effects: Flow<BrowserEffect> = _effects.receiveAsFlow()

    val clipboard = clipboardManager.state

    private var loadJob: Job? = null
    private var hashJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = preferences.preferences.first()
            _state.update { it.copy(sortOrder = saved.sortOrder, viewMode = saved.viewMode) }
            openInitialDirectory(saved.lastDirectoryPaths[panel])
        }
        viewModelScope.launch {
            // Both panels follow the shared view mode (list or grid) too.
            preferences.preferences.map { it.viewMode }.distinctUntilChanged().collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }
        viewModelScope.launch {
            // Both panels follow the shared sort preference.
            preferences.preferences.map { it.sortOrder }.distinctUntilChanged().collect { order ->
                _state.update { it.copy(sortOrder = order, items = it.items.sortedByOrder(order)) }
            }
        }
        viewModelScope.launch {
            // A drive may have been ejected or unplugged: make sure this panel is not left on a dead path.
            volumeChanges.changes.collect {
                delay(VOLUME_SETTLE_MILLIS)
                revalidate()
            }
        }
        viewModelScope.launch {
            // A finished transfer may have changed this folder.
            coordinator.state.map { it.progress?.status }.distinctUntilChanged().collect { status ->
                if (status == TransferStatus.COMPLETED || status == TransferStatus.CANCELLED ||
                    status == TransferStatus.FAILED
                ) {
                    refresh()
                }
            }
        }
    }

    fun onEvent(event: BrowserEvent) {
        when (event) {
            is BrowserEvent.Navigate -> load(event.path)
            BrowserEvent.NavigateUp -> _state.value.parentPath?.let(::load)
            BrowserEvent.Refresh -> revalidate()
            BrowserEvent.ToggleViewMode -> viewModelScope.launch {
                preferences.setViewMode(if (_state.value.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
            }
            BrowserEvent.ToggleSearch -> _state.update { it.copy(searchQuery = if (it.searchQuery == null) "" else null) }
            is BrowserEvent.SetSearchQuery -> _state.update { it.copy(searchQuery = event.query) }
            is BrowserEvent.OpenItem -> when {
                event.item.isDirectory -> load(event.item.path)
                event.item.isRemote() -> emit(BrowserEffect.Message(R.string.error_remote_open))
                ViewerKind.of(event.item.name, event.item.mimeType) != null -> emit(BrowserEffect.OpenViewer(event.item))
                else -> emit(BrowserEffect.OpenFile(event.item, false))
            }
            is BrowserEvent.OpenWith ->
                if (event.item.isRemote()) emit(BrowserEffect.Message(R.string.error_remote_open)) else emit(BrowserEffect.OpenFile(event.item, true))
            is BrowserEvent.ToggleSelection -> toggleSelection(event.item)
            BrowserEvent.SelectAll -> _state.update { s -> s.copy(selectedPaths = s.visibleItems.map { it.path }.toSet()) }
            BrowserEvent.ClearSelection -> _state.update { it.copy(selectedPaths = emptySet()) }
            is BrowserEvent.SortBy -> sortBy(event)
            is BrowserEvent.Copy -> {
                clipboardManager.copy(_state.value.currentPath.orEmpty(), event.items)
                clearSelection()
            }
            is BrowserEvent.Cut -> {
                clipboardManager.cut(_state.value.currentPath.orEmpty(), event.items)
                clearSelection()
            }
            BrowserEvent.Paste -> paste()
            BrowserEvent.CancelClipboard -> clipboardManager.clear()
            is BrowserEvent.Share -> share(event.items)
            is BrowserEvent.RequestDelete -> showDialog(BrowserDialog.ConfirmDelete(event.items))
            is BrowserEvent.RequestRename -> showDialog(BrowserDialog.Rename(event.item))
            BrowserEvent.RequestNewFolder -> showDialog(BrowserDialog.NewFolder)
            is BrowserEvent.RequestCompress -> showDialog(BrowserDialog.Compress(event.items))
            is BrowserEvent.Extract -> archive(OperationType.EXTRACT, event.items, null)
            BrowserEvent.RequestNewFile -> showDialog(BrowserDialog.NewFile)
            is BrowserEvent.ShowProperties -> showProperties(event.item)
            is BrowserEvent.ConfirmName -> confirmName(event.name)
            BrowserEvent.ConfirmDelete -> confirmDelete()
            BrowserEvent.ComputeHash -> computeHash()
            BrowserEvent.DismissDialog -> dismissDialog()
            is BrowserEvent.StartDrag -> startDrag(event.item)
            is BrowserEvent.DropItems -> dragDrop.requestDrop(event.targetPath)
        }
    }

    private suspend fun openInitialDirectory(saved: String?) {
        val root = repository.volumes().firstOrNull()?.rootPath
        val start = saved?.takeIf { repository.listFiles(it).isSuccess } ?: root
        if (start == null) {
            _state.update { it.copy(isLoading = false, errorMessage = NO_STORAGE) }
        } else {
            load(start)
        }
    }

    private fun refresh() {
        _state.value.currentPath?.let { load(it) }
    }

    /**
     * Checks that the current folder still exists. If not (drive ejected, folder deleted from outside),
     * falls back to the nearest ancestor that does, or to a volume root, and tells the user.
     */
    private fun revalidate() {
        viewModelScope.launch {
            val snapshot = _state.value
            val current = snapshot.currentPath ?: return@launch
            _state.update { it.copy(isLoading = true) }
            val volumes = repository.volumes()
            val candidates = snapshot.breadcrumb.reversed().map { it.path } + volumes.map { it.rootPath }
            val target = candidates.firstOrNull { repository.stat(it).isSuccess }
            if (target == null) {
                _state.update {
                    it.copy(isLoading = false, items = emptyList(), selectedPaths = emptySet(), errorMessage = NO_STORAGE)
                }
                return@launch
            }
            if (target == current) {
                load(current, allowFallback = false)
                return@launch
            }
            val volumeRoot = snapshot.breadcrumb.firstOrNull()?.path
            val volumeGone = volumeRoot != null && volumes.none { it.rootPath == volumeRoot }
            emit(BrowserEffect.Message(if (volumeGone) R.string.error_volume_gone else R.string.error_folder_gone))
            load(target, allowFallback = false)
        }
    }

    private fun load(path: String, allowFallback: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.listFiles(path).fold(
                onSuccess = { files ->
                    val breadcrumb = buildBreadcrumb(path)
                    val parent = repository.parentOf(path)
                    _state.update { s ->
                        val sameFolder = s.currentPath == path
                        val sorted = files.sortedByOrder(s.sortOrder)
                        s.copy(
                            currentPath = path,
                            parentPath = parent,
                            breadcrumb = breadcrumb,
                            items = sorted,
                            selectedPaths = if (sameFolder) s.selectedPaths intersect files.map { it.path }.toSet() else emptySet(),
                            searchQuery = if (sameFolder) s.searchQuery else null,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    preferences.setLastDirectory(panel, path)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (allowFallback && _state.value.currentPath != null) {
                        revalidate()
                    } else {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = error.message ?: error.javaClass.simpleName)
                        }
                    }
                },
            )
        }
    }

    private fun toggleSelection(item: FileItem) = _state.update { s ->
        s.copy(selectedPaths = if (item.path in s.selectedPaths) s.selectedPaths - item.path else s.selectedPaths + item.path)
    }

    private fun clearSelection() = _state.update { it.copy(selectedPaths = emptySet()) }

    private fun sortBy(event: BrowserEvent.SortBy) {
        val current = _state.value.sortOrder
        val next = if (current.field == event.field) {
            current.copy(ascending = !current.ascending)
        } else {
            SortOrder(event.field, ascending = true)
        }
        viewModelScope.launch { preferences.setSortOrder(next) }
    }

    private fun paste() {
        val clip = clipboardManager.state.value ?: return
        val target = _state.value.currentPath ?: return
        viewModelScope.launch {
            when (clip.operation) {
                OperationType.COPY -> copyFiles(clip.items, target)
                // Moving into the folder the items already live in changes nothing.
                OperationType.CUT -> if (clip.sourcePath != target) moveFiles(clip.items, target)
                else -> Unit
            }
        }
        clipboardManager.clear()
    }

    /** Network files have no local URI to hand to other apps; they must be copied to the device first. */
    private fun FileItem.isRemote() = path.startsWith("dav://")

    private fun share(items: List<FileItem>) {
        if (items.any { it.isRemote() }) {
            emit(BrowserEffect.Message(R.string.error_remote_open))
            return
        }
        val files = items.filterNot { it.isDirectory }
        if (files.isEmpty()) emit(BrowserEffect.Message(R.string.error_share_folders)) else emit(BrowserEffect.ShareFiles(files))
    }

    private fun showDialog(dialog: BrowserDialog) = _state.update { it.copy(dialog = dialog) }

    /** Opens the sheet with the listed data at once, then enriches it with what only `stat` knows (permissions). */
    private fun showProperties(item: FileItem) {
        showDialog(BrowserDialog.Properties(item))
        viewModelScope.launch {
            repository.stat(item.path).onSuccess { detailed ->
                _state.update { s ->
                    val dialog = s.dialog as? BrowserDialog.Properties
                    if (dialog != null && dialog.item.path == item.path) s.copy(dialog = dialog.copy(item = detailed)) else s
                }
            }
        }
    }

    private fun dismissDialog() {
        hashJob?.cancel()
        _state.update { it.copy(dialog = null) }
    }

    private fun confirmName(name: String) {
        val dialog = _state.value.dialog
        val parent = _state.value.currentPath
        _state.update { it.copy(dialog = null) }
        if (parent == null) return
        if (dialog is BrowserDialog.Compress) {
            archive(OperationType.COMPRESS, dialog.items, name)
            return
        }
        viewModelScope.launch {
            val result = when (dialog) {
                BrowserDialog.NewFolder -> repository.createDirectory(parent, name)
                BrowserDialog.NewFile ->
                    repository.createFile(parent, name, MimeTypes.fromName(name) ?: MimeTypes.OCTET_STREAM)
                is BrowserDialog.Rename -> repository.rename(dialog.item, name)
                else -> return@launch
            }
            result.onFailure { reportFailure(R.string.error_operation_failed, it) }
            refresh()
        }
    }

    /** Queues packing [items] into a ZIP called [archiveName], or unpacking each of them, in the current folder. */
    private fun archive(operation: OperationType, items: List<FileItem>, archiveName: String?) {
        val target = _state.value.currentPath ?: return
        clearSelection()
        coordinator.enqueue(TransferRequest(operation, items, target, archiveName = archiveName))
    }

    private fun confirmDelete() {
        val dialog = _state.value.dialog as? BrowserDialog.ConfirmDelete ?: return
        _state.update { it.copy(dialog = null, selectedPaths = emptySet()) }
        viewModelScope.launch {
            deleteFiles(dialog.items).onFailure { reportFailure(R.string.error_operation_failed, it) }
            refresh()
        }
    }

    private fun computeHash() {
        val dialog = _state.value.dialog as? BrowserDialog.Properties ?: return
        hashJob?.cancel()
        setHash(HashState.Computing)
        hashJob = viewModelScope.launch {
            calculateHash(dialog.item).fold(
                onSuccess = { setHash(HashState.Done(it)) },
                onFailure = { setHash(HashState.Failed(it.message)) },
            )
        }
    }

    private fun setHash(hash: HashState) = _state.update { s ->
        val dialog = s.dialog as? BrowserDialog.Properties ?: return@update s
        s.copy(dialog = dialog.copy(hash = hash))
    }

    private fun startDrag(item: FileItem) {
        val s = _state.value
        val path = s.currentPath ?: return
        val items = if (item.path in s.selectedPaths) s.selectedItems else listOf(item)
        dragDrop.start(DragPayload(items, panel, path))
    }

    private fun reportFailure(@androidx.annotation.StringRes resId: Int, error: Throwable) {
        emit(BrowserEffect.Message(resId, error.message))
    }

    private fun emit(effect: BrowserEffect) {
        _effects.trySend(effect)
    }

    companion object {
        /** Time the system needs to finish mounting or unmounting before the volumes are re-read. */
        private const val VOLUME_SETTLE_MILLIS = 500L

        /** Marker stored in [BrowserState.errorMessage] when there is no volume to browse. */
        const val NO_STORAGE = "no-storage"
    }
}
