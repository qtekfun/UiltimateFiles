package com.qtekfun.ultimatefiles.ui.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ConflictDecision
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
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import com.qtekfun.ultimatefiles.domain.transfer.TransferCoordinator
import com.qtekfun.ultimatefiles.domain.usecase.BatchCopyUseCase
import com.qtekfun.ultimatefiles.domain.usecase.BatchMoveUseCase
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShortcutKind { DOWNLOADS, DOCUMENTS, PHOTOS }

data class Shortcut(val kind: ShortcutKind, @StringRes val labelRes: Int, val path: String)

data class MainState(
    val volumes: List<StorageVolume> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val activePanel: PanelId = PanelId.LEFT,
)

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

    init {
        viewModelScope.launch(Dispatchers.IO) { coordinator.loadInterrupted() }
        refreshVolumes()
        viewModelScope.launch {
            volumeChanges.changes.collect {
                delay(VOLUME_SETTLE_MILLIS)
                refreshVolumes()
            }
        }
    }

    fun setActivePanel(panel: PanelId) = _state.update { it.copy(activePanel = panel) }

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
        val SHORTCUT_FOLDERS = listOf(
            Triple(ShortcutKind.DOWNLOADS, R.string.shortcut_downloads, "Download"),
            Triple(ShortcutKind.DOCUMENTS, R.string.shortcut_documents, "Documents"),
            Triple(ShortcutKind.PHOTOS, R.string.shortcut_photos, "DCIM"),
        )
    }
}
