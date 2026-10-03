package com.qtekfun.fexplo.ui.main

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.PanelId
import com.qtekfun.fexplo.core.model.StorageKind
import com.qtekfun.fexplo.core.model.StorageVolume
import com.qtekfun.fexplo.data.system.IntentFactory
import com.qtekfun.fexplo.ui.browser.BrowserEvent
import com.qtekfun.fexplo.ui.browser.BrowserViewModel
import com.qtekfun.fexplo.ui.components.AddAccountDialog
import com.qtekfun.fexplo.ui.components.ConflictDialog
import com.qtekfun.fexplo.ui.components.DropActionDialog
import com.qtekfun.fexplo.ui.components.TransferProgressBar
import com.qtekfun.fexplo.ui.dualpanel.DualPanelScaffold
import com.qtekfun.fexplo.ui.history.HistoryScreen
import com.qtekfun.fexplo.ui.history.HistoryViewModel
import com.qtekfun.fexplo.ui.settings.SettingsScreen
import com.qtekfun.fexplo.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

private enum class AppScreen { BROWSER, HISTORY, SETTINGS }

/** Root screen: navigation drawer + dual panel, plus the dialogs and bar shared by both panels. */
@Composable
fun MainScreen() {
    val koin = remember { KoinPlatform.getKoin() }
    val viewModel = viewModel<MainViewModel>(factory = viewModelFactory { initializer { koin.get<MainViewModel>() } })
    val left = rememberBrowserViewModel(PanelId.LEFT)
    val right = rememberBrowserViewModel(PanelId.RIGHT)

    val settingsViewModel = viewModel<SettingsViewModel>(factory = viewModelFactory { initializer { koin.get<SettingsViewModel>() } })
    val historyViewModel = viewModel<HistoryViewModel>(factory = viewModelFactory { initializer { koin.get<HistoryViewModel>() } })
    var screen by rememberSaveable { mutableStateOf(AppScreen.BROWSER) }
    var showAddAccount by rememberSaveable { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<StorageVolume?>(null) }
    BackHandler(enabled = screen != AppScreen.BROWSER) { screen = AppScreen.BROWSER }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val pendingDrop by viewModel.pendingDrop.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) viewModel.refreshVolumes()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addStorage)
    }

    fun openInActivePanel(path: String) {
        (if (state.activePanel == PanelId.LEFT) left else right).onEvent(BrowserEvent.Navigate(path))
        scope.launch { drawerState.close() }
    }

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
                onShowHistory = {
                    screen = AppScreen.HISTORY
                    scope.launch { drawerState.close() }
                },
                onShowSettings = {
                    screen = AppScreen.SETTINGS
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
                when (screen) {
                    AppScreen.BROWSER -> DualPanelScaffold(
                        left = left,
                        right = right,
                        activePanel = state.activePanel,
                        onActivePanelChange = viewModel::setActivePanel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                    AppScreen.HISTORY -> {
                        val entries by historyViewModel.entries.collectAsStateWithLifecycle()
                        HistoryScreen(
                            entries = entries,
                            onClear = historyViewModel::clear,
                            onBack = { screen = AppScreen.BROWSER },
                        )
                    }
                    AppScreen.SETTINGS -> {
                        val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
                        preferences?.let { current ->
                            SettingsScreen(
                                preferences = current,
                                onThemeMode = settingsViewModel::setThemeMode,
                                onDynamicColor = settingsViewModel::setDynamicColor,
                                onVerifyCopies = settingsViewModel::setVerifyCopies,
                                onBack = { screen = AppScreen.BROWSER },
                            )
                        }
                    }
                }
            }
            TransferProgressBar(progress = transfer.progress, onCancel = viewModel::cancelTransfers)
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onConnect = viewModel::connectAccount,
            onCancel = viewModel::cancelConnect,
            onDismiss = { showAddAccount = false },
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
private fun rememberBrowserViewModel(panel: PanelId): BrowserViewModel {
    val koin = remember { KoinPlatform.getKoin() }
    return viewModel(
        key = panel.name,
        factory = viewModelFactory {
            initializer { koin.get<BrowserViewModel>(parameters = { parametersOf(panel) }) }
        },
    )
}

@Composable
private fun DrawerContent(
    volumes: List<StorageVolume>,
    shortcuts: List<Shortcut>,
    onOpen: (String) -> Unit,
    onAddStorage: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (StorageVolume) -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onEject: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
            volumes.forEach { volume ->
                NavigationDrawerItem(
                    label = { Text(volume.label) },
                    icon = { Icon(volume.icon(), contentDescription = null) },
                    badge = {
                        if (volume.kind == StorageKind.NETWORK) {
                            IconButton(onClick = { onRemoveAccount(volume) }) {
                                Icon(Icons.Filled.CloudOff, contentDescription = stringResource(R.string.account_remove))
                            }
                        } else if (volume.isEjectable) {
                            IconButton(onClick = onEject) {
                                Icon(Icons.Filled.Eject, contentDescription = stringResource(R.string.drawer_eject))
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
