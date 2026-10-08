package com.qtekfun.ultimatefiles.ui.main

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.BuildConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.StorageKind
import com.qtekfun.ultimatefiles.core.model.StorageVolume
import com.qtekfun.ultimatefiles.data.system.IntentFactory
import com.qtekfun.ultimatefiles.ui.browser.BrowserEvent
import com.qtekfun.ultimatefiles.ui.browser.BrowserViewModel
import com.qtekfun.ultimatefiles.ui.analysis.SizeAnalysisScreen
import com.qtekfun.ultimatefiles.ui.analysis.SizeAnalysisState
import com.qtekfun.ultimatefiles.ui.analysis.SizeAnalysisViewModel
import com.qtekfun.ultimatefiles.ui.components.AccountEdit
import com.qtekfun.ultimatefiles.ui.components.AddAccountDialog
import com.qtekfun.ultimatefiles.ui.components.BatteryHintDialog
import com.qtekfun.ultimatefiles.ui.components.InterruptedTransfersDialog
import com.qtekfun.ultimatefiles.ui.components.NameInputDialog
import com.qtekfun.ultimatefiles.ui.components.rememberIgnoringBatteryOptimizations
import com.qtekfun.ultimatefiles.ui.components.requestIgnoreBatteryOptimizations
import com.qtekfun.ultimatefiles.ui.components.ConflictDialog
import com.qtekfun.ultimatefiles.ui.components.DropActionDialog
import com.qtekfun.ultimatefiles.ui.components.TransferProgressBar
import com.qtekfun.ultimatefiles.data.thumbnail.ThumbnailLoader
import com.qtekfun.ultimatefiles.ui.browser.LocalThumbnailLoader
import com.qtekfun.ultimatefiles.ui.dualpanel.DualPanelScaffold
import com.qtekfun.ultimatefiles.ui.dualpanel.PanelEntry
import com.qtekfun.ultimatefiles.ui.history.HistoryScreen
import com.qtekfun.ultimatefiles.ui.history.HistoryViewModel
import com.qtekfun.ultimatefiles.ui.settings.SettingsScreen
import com.qtekfun.ultimatefiles.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import com.qtekfun.ultimatefiles.data.system.IncomingFiles
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import org.koin.mp.KoinPlatform

private enum class AppScreen { BROWSER, HISTORY, SETTINGS, ANALYSIS }

/** Root screen: navigation drawer + dual panel, plus the dialogs and bar shared by both panels. */
@Composable
fun MainScreen() {
    val koin = remember { KoinPlatform.getKoin() }
    val viewModel = viewModel<MainViewModel>(factory = viewModelFactory { initializer { koin.get<MainViewModel>() } })
    val settingsViewModel = viewModel<SettingsViewModel>(factory = viewModelFactory { initializer { koin.get<SettingsViewModel>() } })
    val historyViewModel = viewModel<HistoryViewModel>(factory = viewModelFactory { initializer { koin.get<HistoryViewModel>() } })
    var screen by rememberSaveable { mutableStateOf(AppScreen.BROWSER) }
    val analysisViewModel = viewModel<SizeAnalysisViewModel>(factory = viewModelFactory { initializer { koin.get<SizeAnalysisViewModel>() } })
    // What is being analysed is kept apart from the view model so that the screen can start again after the process died.
    var analysisPath by rememberSaveable { mutableStateOf<String?>(null) }
    var analysisLabel by rememberSaveable { mutableStateOf("") }
    fun startAnalysis(path: String, label: String) {
        analysisPath = path
        analysisLabel = label
        screen = AppScreen.ANALYSIS
        analysisViewModel.start(path, label)
    }
    fun leaveAnalysis() {
        analysisViewModel.reset()
        analysisPath = null
        screen = AppScreen.BROWSER
    }
    var showAddAccount by rememberSaveable { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<StorageVolume?>(null) }
    var accountToRename by remember { mutableStateOf<StorageVolume?>(null) }
    var accountToEdit by remember { mutableStateOf<EditableAccount?>(null) }
    BackHandler(enabled = screen != AppScreen.BROWSER && screen != AppScreen.ANALYSIS) { screen = AppScreen.BROWSER }
    // Back goes up one folder inside the analysis first, and only then leaves it.
    BackHandler(enabled = screen == AppScreen.ANALYSIS) { if (!analysisViewModel.up()) leaveAnalysis() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val panels = state.panels.map { id -> key(id) { PanelEntry(id, rememberBrowserViewModel(id, viewModel)) } }
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val pendingDrop by viewModel.pendingDrop.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Closing a panel can be undone for a few seconds.
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.closedPanels.collect { closed ->
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.panel_closed),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restorePanel(closed)
        }
    }

    // Panels showing an account that was just edited reload it, so they do not keep using the old connection's listing.
    LaunchedEffect(viewModel) {
        viewModel.accountChanges.collect { id ->
            panels.forEach { entry ->
                val path = entry.viewModel.state.value.currentPath.orEmpty()
                if (listOf("dav", "sftp", "smb").any { path.startsWith("$it://$id") }) entry.viewModel.onEvent(BrowserEvent.Refresh)
            }
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) viewModel.refreshVolumes()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addStorage)
    }

    fun openInActivePanel(path: String) {
        panels.firstOrNull { it.id == state.activePanel }?.viewModel?.onEvent(BrowserEvent.Navigate(path))
        scope.launch { drawerState.close() }
    }

    // An archive another app sent here opens as a folder in the active panel.
    val incomingFiles = remember { koin.get<IncomingFiles>() }
    val incoming by incomingFiles.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        incoming?.let { path ->
            screen = AppScreen.BROWSER
            openInActivePanel(ArchivePaths.rootOf(path))
            incomingFiles.consume()
        }
    }

    CompositionLocalProvider(LocalThumbnailLoader provides remember { koin.get<ThumbnailLoader>() }) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = screen == AppScreen.BROWSER,
            drawerContent = {
                DrawerContent(
                    volumes = state.volumes,
                    shortcuts = state.shortcuts,
                    onOpen = ::openInActivePanel,
                    onAddStorage = { pickFolder.launch(null) },
                    onAddAccount = {
                        showAddAccount = true
                        scope.launch { drawerState.close() }
                    },
                    onRemoveAccount = { accountToRemove = it },
                        onRenameAccount = { accountToRename = it },
                        onEditAccount = { volume -> viewModel.loadForEdit(volume) { accountToEdit = it } },
                    onShowHistory = {
                        screen = AppScreen.HISTORY
                        scope.launch { drawerState.close() }
                    },
                    onShowSettings = {
                        screen = AppScreen.SETTINGS
                        scope.launch { drawerState.close() }
                    },
                    onAnalyze = { volume ->
                        startAnalysis(volume.rootPath, volume.label)
                        scope.launch { drawerState.close() }
                    },
                    onEject = {
                        try {
                            context.startActivity(KoinPlatform.getKoin().get<IntentFactory>().ejectSettings())
                        } catch (e: ActivityNotFoundException) {
                            // No storage settings screen on this device; nothing else can be done safely.
                        }
                    },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).zIndex(1f))
                    when (screen) {
                        AppScreen.BROWSER -> DualPanelScaffold(
                            panels = panels,
                            startPanel = state.startPanel,
                            endPanel = state.endPanel,
                            activePanel = state.activePanel,
                            barPosition = state.panelBarPosition,
                            onActivePanelChange = viewModel::setActivePanel,
                            onShowPanel = viewModel::showPanel,
                            onAddPanel = {
                                val active = panels.firstOrNull { it.id == state.activePanel }
                                viewModel.addPanel(active?.viewModel?.state?.value?.currentPath)
                            },
                            onClosePanel = { id ->
                                viewModel.closePanel(id, panels.firstOrNull { it.id == id }?.viewModel?.state?.value?.currentPath)
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onAnalyze = ::startAnalysis,
                        )
                        AppScreen.ANALYSIS -> {
                            val analysis by analysisViewModel.state.collectAsStateWithLifecycle()
                            LaunchedEffect(analysis) {
                                // After the process died the view model is empty again: start over, or leave if nothing was being analysed.
                                if (analysis is SizeAnalysisState.Idle) {
                                    analysisPath?.let { analysisViewModel.start(it, analysisLabel) } ?: run { screen = AppScreen.BROWSER }
                                }
                            }
                            SizeAnalysisScreen(
                                state = analysis,
                                onBack = { if (!analysisViewModel.up()) leaveAnalysis() },
                                onOpen = analysisViewModel::open,
                                onCancel = ::leaveAnalysis,
                                onRetry = analysisViewModel::start,
                                onShowInPanel = { item ->
                                    panels.firstOrNull { it.id == state.activePanel }?.viewModel?.onEvent(BrowserEvent.Reveal(item))
                                    leaveAnalysis()
                                },
                                onOpenInPanel = { item ->
                                    panels.firstOrNull { it.id == state.activePanel }?.viewModel?.onEvent(BrowserEvent.Navigate(item.path))
                                    leaveAnalysis()
                                },
                            )
                        }
                        AppScreen.HISTORY -> {
                            val entries by historyViewModel.entries.collectAsStateWithLifecycle()
                            val running by historyViewModel.transfer.collectAsStateWithLifecycle()
                            HistoryScreen(
                                entries = entries,
                                running = running,
                                onTogglePause = historyViewModel::togglePause,
                                onCancelRunning = historyViewModel::cancelRunning,
                                onClear = historyViewModel::clear,
                                onBack = { screen = AppScreen.BROWSER },
                            )
                        }
                        AppScreen.SETTINGS -> {
                            val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
                            preferences?.let { current ->
                                val outcome by settingsViewModel.outcome.collectAsStateWithLifecycle()
                                val accountCount by settingsViewModel.accountCount.collectAsStateWithLifecycle()
                                SettingsScreen(
                                    preferences = current,
                                    accountCount = accountCount,
                                    outcome = outcome,
                                    onExport = settingsViewModel::exportBackup,
                                    onImport = settingsViewModel::importBackup,
                                    onDismissOutcome = settingsViewModel::dismissOutcome,
                                    onThemeMode = settingsViewModel::setThemeMode,
                                    onDynamicColor = settingsViewModel::setDynamicColor,
                                    onVerifyCopies = settingsViewModel::setVerifyCopies,
                                    onThumbnailsOnNetwork = settingsViewModel::setThumbnailsOnNetwork,
                                    onPanelBarPosition = settingsViewModel::setPanelBarPosition,
                                    onBack = { screen = AppScreen.BROWSER },
                                )
                            }
                        }
                    }
                }
                TransferProgressBar(
                    progress = transfer.progress,
                    paused = transfer.paused,
                    onTogglePause = viewModel::togglePauseTransfers,
                    onCancel = viewModel::cancelTransfers,
                )
            }
        }
    }

    val interrupted by viewModel.interrupted.collectAsStateWithLifecycle()
    if (interrupted.isNotEmpty()) {
        InterruptedTransfersDialog(
            tasks = interrupted,
            onResume = viewModel::resumeInterrupted,
            onDiscard = viewModel::discardInterrupted,
        )
    }

    val batteryExempt by rememberIgnoringBatteryOptimizations()
    val hints = remember { context.getSharedPreferences("hints", android.content.Context.MODE_PRIVATE) }
    var batteryHintAnswered by remember { mutableStateOf(hints.getBoolean(KEY_BATTERY_HINT, false)) }
    if (transfer.active != null && !batteryExempt && !batteryHintAnswered) {
        BatteryHintDialog(
            onAllow = {
                batteryHintAnswered = true
                hints.edit().putBoolean(KEY_BATTERY_HINT, true).apply()
                requestIgnoreBatteryOptimizations(context)
            },
            onDismiss = {
                batteryHintAnswered = true
                hints.edit().putBoolean(KEY_BATTERY_HINT, true).apply()
            },
        )
    }

    if (showAddAccount) {
        AddAccountDialog(
            onConnect = viewModel::connectAccount,
            onConnectSftp = viewModel::connectSftp,
            onConnectSmb = viewModel::connectSmb,
            onCancel = viewModel::cancelConnect,
            onDismiss = { showAddAccount = false },
        )
    }
    accountToEdit?.let { editable ->
        AddAccountDialog(
            onConnect = viewModel::connectAccount,
            onConnectSftp = viewModel::connectSftp,
            onConnectSmb = viewModel::connectSmb,
            onCancel = viewModel::cancelConnect,
            onDismiss = { accountToEdit = null },
            edit = AccountEdit(
                account = editable.account,
                usesKey = editable.usesKey,
                onUpdateNextcloud = { server, label, trust, signIn, openBrowser, onDone ->
                    viewModel.updateNextcloud(editable.account, server, label, trust, signIn, openBrowser, onDone)
                },
                onUpdateSftp = { host, port, user, secret, key, useKey, label, pinned, onDone ->
                    viewModel.updateSftp(editable.account, host, port, user, secret, key, useKey, label, pinned, onDone)
                },
                onUpdateSmb = { host, port, share, domain, user, password, label, onDone ->
                    viewModel.updateSmb(editable.account, host, port, share, domain, user, password, label, onDone)
                },
            ),
        )
    }
    accountToRename?.let { volume ->
        NameInputDialog(
            title = R.string.action_rename,
            initialName = volume.label,
            onConfirm = {
                viewModel.renameAccount(volume, it)
                accountToRename = null
            },
            onDismiss = { accountToRename = null },
        )
    }
    accountToRemove?.let { volume ->
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text(stringResource(R.string.account_remove_title)) },
            text = { Text(stringResource(R.string.account_remove_message, volume.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAccount(volume)
                    accountToRemove = null
                }) { Text(stringResource(R.string.account_remove)) }
            },
            dismissButton = { TextButton(onClick = { accountToRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    transfer.conflict?.let { prompt ->
        ConflictDialog(
            prompt = prompt,
            onDecision = viewModel::answerConflict,
            onCancelTransfer = viewModel::cancelTransfers,
        )
    }
    pendingDrop?.let { drop ->
        DropActionDialog(
            itemCount = drop.items.size,
            onCopy = { viewModel.confirmDrop(copy = true) },
            onMove = { viewModel.confirmDrop(copy = false) },
            onDismiss = viewModel::dismissDrop,
        )
    }
}

@Composable
private fun rememberBrowserViewModel(panel: PanelId, main: MainViewModel): BrowserViewModel {
    val koin = remember { KoinPlatform.getKoin() }
    // Each panel has a store of its own in [main] so that closing one disposes of just its state holder.
    val owner = remember(panel) {
        object : ViewModelStoreOwner {
            override val viewModelStore = main.storeFor(panel)
        }
    }
    return viewModel(
        viewModelStoreOwner = owner,
        key = panel.value.toString(),
        factory = viewModelFactory {
            initializer { koin.get<BrowserViewModel>(parameters = { parametersOf(panel) }) }
        },
    )
}

/** The three-dot menu of a network account in the drawer. */
@Composable
private fun AccountMenu(onEdit: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_edit)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_remove)) },
                leadingIcon = { Icon(Icons.Filled.CloudOff, contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** The app's icon, name and version at the top of the drawer. */
@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A thin bar of how full the volume is and how much room is left; nothing for volumes that cannot tell. */
@Composable
private fun SpaceUsage(volume: StorageVolume) {
    val total = volume.totalBytes ?: return
    val free = volume.freeBytes ?: return
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.drawer_space_free, formatBytes(free), formatBytes(total)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerContent(
    volumes: List<StorageVolume>,
    shortcuts: List<Shortcut>,
    onOpen: (String) -> Unit,
    onAddStorage: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (StorageVolume) -> Unit,
    onRenameAccount: (StorageVolume) -> Unit,
    onEditAccount: (StorageVolume) -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onAnalyze: (StorageVolume) -> Unit,
    onEject: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DrawerHeader()
            volumes.forEach { volume ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(volume.label)
                            SpaceUsage(volume)
                        }
                    },
                    icon = { Icon(volume.icon(), contentDescription = null) },
                    badge = {
                        if (volume.kind == StorageKind.NETWORK) {
                            AccountMenu(onEdit = { onEditAccount(volume) }, onRename = { onRenameAccount(volume) }, onRemove = { onRemoveAccount(volume) })
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onAnalyze(volume) }) {
                                    Icon(Icons.Filled.DataUsage, contentDescription = stringResource(R.string.action_analyze_size))
                                }
                                if (volume.isEjectable) {
                                    IconButton(onClick = onEject) {
                                        Icon(Icons.Filled.Eject, contentDescription = stringResource(R.string.drawer_eject))
                                    }
                                }
                            }
                        }
                    },
                    selected = false,
                    onClick = { onOpen(volume.rootPath) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (shortcuts.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            shortcuts.forEach { shortcut ->
                NavigationDrawerItem(
                    label = { Text(stringResource(shortcut.labelRes)) },
                    icon = { Icon(shortcut.kind.icon(), contentDescription = null) },
                    selected = false,
                    onClick = { onOpen(shortcut.path) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_add_storage)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                selected = false,
                onClick = onAddStorage,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_add_account)) },
                icon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                selected = false,
                onClick = onAddAccount,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.history_title)) },
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                selected = false,
                onClick = onShowHistory,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title)) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                selected = false,
                onClick = onShowSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

private fun StorageVolume.icon(): ImageVector = when (kind) {
    StorageKind.INTERNAL -> Icons.Filled.PhoneAndroid
    StorageKind.NETWORK -> Icons.Filled.Cloud
    else -> Icons.Filled.Usb
}

private fun ShortcutKind.icon(): ImageVector = when (this) {
    ShortcutKind.DOWNLOADS -> Icons.Filled.Download
    ShortcutKind.DOCUMENTS -> Icons.Filled.Description
    ShortcutKind.PHOTOS -> Icons.Filled.PhotoLibrary
}

private const val KEY_BATTERY_HINT = "battery_hint_answered"
