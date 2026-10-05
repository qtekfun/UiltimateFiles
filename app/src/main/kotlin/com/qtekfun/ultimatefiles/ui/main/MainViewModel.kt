package com.qtekfun.ultimatefiles.ui.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ConflictDecision
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.data.network.TrustChoice
import com.qtekfun.ultimatefiles.data.network.SftpAccountService
import com.qtekfun.ultimatefiles.data.network.SmbAccountService
import com.qtekfun.ultimatefiles.data.network.WebDavAccountService
import com.qtekfun.ultimatefiles.data.repository.SafFileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import com.qtekfun.ultimatefiles.domain.transfer.TransferCoordinator
import com.qtekfun.ultimatefiles.domain.usecase.BatchCopyUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BatchMoveUseCase
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShortcutKind { DOWNLOADS, DOCUMENTS, PHOTOS }

data class Shortcut(val kind: ShortcutKind, @StringRes val labelRes: Int, val path: String)

data class MainState(
    val volumes: List<StorageVolume> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    /** The open panels in display order; empty until the saved list has been read. */
    val panels: List<PanelId> = emptyList(),
    /** Panels shown on the left and right in landscape. */
    val startPanel: PanelId = PanelId.LEFT,
    val endPanel: PanelId = PanelId.RIGHT,
    val activePanel: PanelId = PanelId.LEFT,
    val panelBarPosition: PanelBarPosition = PanelBarPosition.TOP,
)

enum class PanelSlot { START, END }

/** A panel the user closed, kept just long enough to offer undoing it. */
data class ClosedPanel(val index: Int, val path: String?)

/** App-wide state: drawer contents, the panel that is "active", and transfers/drops shared by both panels. */
class MainViewModel(
    private val repository: FileSystemRepository,
    private val safRepository: SafFileSystemRepository,
    private val coordinator: TransferCoordinator,
    private val dragDrop: DragDropState,
    private val copyFiles: BatchCopyUseCase,
    private val moveFiles: BatchMoveUseCase,
    private val volumeChanges: VolumeChangeSource,
    private val accountService: WebDavAccountService,
    private val accountRepository: AccountRepository,
    private val sftpAccountService: SftpAccountService,
    private val smbAccountService: SmbAccountService,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    val transfer = coordinator.state
    val pendingDrop = dragDrop.pendingDrop

    /** Copies the system stopped before they finished; the screen offers to resume or discard them. */
    val interrupted = coordinator.interrupted

    fun resumeInterrupted() {
        viewModelScope.launch { coordinator.restoreInterrupted() }
    }

    fun discardInterrupted() = coordinator.discardInterrupted()

    /**
     * One store per panel, so closing a panel can dispose of its [com.qtekfun.ultimatefiles.ui.browser.BrowserViewModel]
     * while the others survive. They live here to outlast configuration changes such as rotation.
     */
    private val panelStores = mutableMapOf<PanelId, ViewModelStore>()

    private val _closedPanels = Channel<ClosedPanel>(Channel.BUFFERED)
    val closedPanels: Flow<ClosedPanel> = _closedPanels.receiveAsFlow()

    fun storeFor(panel: PanelId): ViewModelStore = panelStores.getOrPut(panel) { ViewModelStore() }

    override fun onCleared() {
        panelStores.values.forEach { it.clear() }
        panelStores.clear()
    }

    init {
        viewModelScope.launch {
            val saved = preferences.preferences.first().panelIds
            _state.update { it.copy(panels = saved, startPanel = saved[0], endPanel = saved[1], activePanel = saved[0]) }
        }
        viewModelScope.launch {
            preferences.preferences.map { it.panelBarPosition }.distinctUntilChanged().collect { position ->
                _state.update { it.copy(panelBarPosition = position) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { coordinator.loadInterrupted() }
        refreshVolumes()
        viewModelScope.launch {
            volumeChanges.changes.collect {
                delay(VOLUME_SETTLE_MILLIS)
                refreshVolumes()
            }
        }
    }

    /**
     * Makes [panel] the active one. A panel that is not on screen takes the place of the active
     * one, so the active panel is always visible (landscape shows two, portrait one).
     */
    fun setActivePanel(panel: PanelId) = _state.update { s ->
        when {
            panel !in s.panels -> s
            panel == s.startPanel || panel == s.endPanel -> s.copy(activePanel = panel)
            s.activePanel == s.endPanel -> s.copy(endPanel = panel, activePanel = panel)
            else -> s.copy(startPanel = panel, activePanel = panel)
        }
    }

    /** Shows [panel] in [slot] (landscape); if it was in the other slot, the two swap places. */
    fun showPanel(panel: PanelId, slot: PanelSlot) = _state.update { s ->
        if (panel !in s.panels) return@update s
        when (slot) {
            PanelSlot.START -> s.copy(
                startPanel = panel,
                endPanel = if (panel == s.endPanel) s.startPanel else s.endPanel,
                activePanel = panel,
            )
            PanelSlot.END -> s.copy(
                endPanel = panel,
                startPanel = if (panel == s.startPanel) s.endPanel else s.startPanel,
                activePanel = panel,
            )
        }
    }

    /** Opens a new panel right after the active one, starting in [initialPath], and makes it the active one. */
    fun addPanel(initialPath: String?) = insertPanel(_state.value.panels.indexOf(_state.value.activePanel) + 1, initialPath)

    private fun insertPanel(index: Int, initialPath: String?) {
        viewModelScope.launch {
            val current = _state.value
            val panel = PanelId((current.panels.maxOfOrNull { it.value } ?: -1) + 1)
            val panels = current.panels.toMutableList().apply { add(index.coerceIn(0, size), panel) }
            // The panel reads its saved directory when created, so write it before the panel exists.
            initialPath?.let { preferences.setLastDirectory(panel, it) }
            preferences.setPanelIds(panels)
            _state.update { s ->
                // The new panel takes the place of the active one on screen.
                if (s.activePanel == s.endPanel) s.copy(panels = panels, endPanel = panel, activePanel = panel)
                else s.copy(panels = panels, startPanel = panel, activePanel = panel)
            }
        }
    }

    /**
     * Closes [panel] (whose folder was [lastPath]); at least [MIN_PANELS] always remain. The panel can
     * be brought back through [closedPanels] and [restorePanel].
     */
    fun closePanel(panel: PanelId, lastPath: String?) {
        val current = _state.value
        val index = current.panels.indexOf(panel)
        if (index < 0 || current.panels.size <= MIN_PANELS) return
        val panels = current.panels - panel
        // A slot that showed the closed panel falls back to the nearest panel the other slot is not showing.
        fun replacement(other: PanelId) = panels.filter { it != other }.let { it[index.coerceAtMost(it.lastIndex)] }
        val start = if (current.startPanel == panel) replacement(current.endPanel) else current.startPanel
        val end = if (current.endPanel == panel) replacement(current.startPanel) else current.endPanel
        val active = if (current.activePanel == panel) (if (current.startPanel == panel) start else end) else current.activePanel
        _state.update { it.copy(panels = panels, startPanel = start, endPanel = end, activePanel = active) }
        panelStores.remove(panel)?.clear()
        viewModelScope.launch { preferences.setPanelIds(panels) }
        _closedPanels.trySend(ClosedPanel(index, lastPath))
    }

    /** Opens a panel again where [closed] was, in the folder it had. */
    fun restorePanel(closed: ClosedPanel) = insertPanel(closed.index, closed.path)

    /** Re-reads the volumes, e.g. after a USB drive was plugged in or a folder was granted. */
    fun refreshVolumes() {
        viewModelScope.launch {
            val volumes = repository.volumes()
            _state.update { it.copy(volumes = volumes, shortcuts = findShortcuts(volumes)) }
        }
    }

    fun addStorage(treeUri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { safRepository.addTree(treeUri) } }
            refreshVolumes()
        }
    }

    private var loginJob: Job? = null

    /**
     * Signs in to a Nextcloud server through Login Flow v2. [openBrowser] gets the approval page;
     * [onDone] runs on the main thread with the outcome. A second call replaces a pending one.
     */
    fun connectAccount(
        serverUrl: String,
        label: String,
        trust: TrustChoice,
        openBrowser: (String) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            val result = accountService.connectWithLoginFlow(serverUrl, label, trust, openBrowser).map { }
            if (result.isSuccess) refreshVolumes()
            onDone(result)
        }
    }

    /** Adds an SFTP server. [pinnedFingerprint] is null on the first try; the UI asks the user about the key it reports. */
    fun connectSftp(
        host: String,
        port: Int,
        username: String,
        password: String,
        label: String,
        pinnedFingerprint: String?,
        onDone: (Result<Unit>) -> Unit,
    ) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            val result = sftpAccountService.connect(host, port, username, password, label, pinnedFingerprint).map { }
            if (result.isSuccess) refreshVolumes()
            onDone(result)
        }
    }

    fun connectSmb(
        host: String,
        port: Int,
        share: String,
        domain: String,
        username: String,
        password: String,
        label: String,
        onDone: (Result<Unit>) -> Unit,
    ) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            val result = smbAccountService.connect(host, port, share, domain, username, password, label).map { }
            if (result.isSuccess) refreshVolumes()
            onDone(result)
        }
    }

    fun cancelConnect() {
        loginJob?.cancel()
        loginJob = null
    }

    /** Forgets the account behind a network [volume]; nothing is deleted on the server. */
    fun removeAccount(volume: StorageVolume) {
        viewModelScope.launch {
            accountRepository.remove(volume.id.removePrefix("dav:").removePrefix("sftp:").removePrefix("smb:"))
            refreshVolumes()
        }
    }

    /** Gives the account behind a network [volume] another name; blank names are ignored. */
    fun renameAccount(volume: StorageVolume, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            accountRepository.rename(volume.id.removePrefix("dav:").removePrefix("sftp:").removePrefix("smb:"), trimmed)
            refreshVolumes()
        }
    }

    fun answerConflict(decision: ConflictDecision) = coordinator.answerConflict(decision)

    fun cancelTransfers() = coordinator.cancelAll()

    fun togglePauseTransfers() = coordinator.togglePause()

    fun confirmDrop(copy: Boolean) {
        val drop = dragDrop.pendingDrop.value ?: return
        viewModelScope.launch {
            if (copy) copyFiles(drop.items, drop.targetPath) else moveFiles(drop.items, drop.targetPath)
        }
        dragDrop.clearPendingDrop()
    }

    fun dismissDrop() = dragDrop.clearPendingDrop()

    private suspend fun findShortcuts(volumes: List<StorageVolume>): List<Shortcut> {
        val root = volumes.firstOrNull { it.kind == StorageKind.INTERNAL }?.rootPath ?: return emptyList()
        val folders = repository.listFiles(root).getOrNull().orEmpty().filter { it.isDirectory }
        return SHORTCUT_FOLDERS.mapNotNull { (kind, label, folderName) ->
            folders.firstOrNull { it.name.equals(folderName, ignoreCase = true) }
                ?.let { Shortcut(kind, label, it.path) }
        }
    }

    private companion object {
        const val VOLUME_SETTLE_MILLIS = 500L
        const val MIN_PANELS = 2
        val SHORTCUT_FOLDERS = listOf(
            Triple(ShortcutKind.DOWNLOADS, R.string.shortcut_downloads, "Download"),
            Triple(ShortcutKind.DOCUMENTS, R.string.shortcut_documents, "Documents"),
            Triple(ShortcutKind.PHOTOS, R.string.shortcut_photos, "DCIM"),
        )
    }
}
