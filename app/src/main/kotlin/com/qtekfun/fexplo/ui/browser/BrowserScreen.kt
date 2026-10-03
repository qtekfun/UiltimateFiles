package com.qtekfun.fexplo.ui.browser

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.data.system.IntentFactory
import com.qtekfun.fexplo.ui.components.ConfirmDeleteDialog
import com.qtekfun.fexplo.ui.components.DockedPasteBar
import com.qtekfun.fexplo.ui.components.NameInputDialog
import com.qtekfun.fexplo.ui.components.PropertiesBottomSheet
import org.koin.mp.KoinPlatform

/**
 * One file panel: top bar (breadcrumb, or the CAB while selecting), the file list with
 * pull-to-refresh, and the docked paste bar. Two instances make up the dual panel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    isActive: Boolean,
    dragAndDropEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    topInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val intents = remember { KoinPlatform.getKoin().get<IntentFactory>() }
    val snackbar = remember { SnackbarHostState() }
    val onEvent = viewModel::onEvent

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BrowserEffect.OpenFile -> try {
                    context.startActivity(intents.view(effect.item, effect.chooser))
                } catch (e: ActivityNotFoundException) {
                    snackbar.showSnackbar(resources.getString(R.string.error_no_app))
                }
                is BrowserEffect.ShareFiles -> try {
                    context.startActivity(intents.share(effect.items))
                } catch (e: ActivityNotFoundException) {
                    snackbar.showSnackbar(resources.getString(R.string.error_no_app))
                }
                is BrowserEffect.Message -> snackbar.showSnackbar(
                    listOfNotNull(resources.getString(effect.resId), effect.detail).joinToString(": "),
                )
            }
        }
    }

    BackHandler(enabled = isActive && (state.isSelecting || state.searchQuery != null || state.parentPath != null)) {
        onEvent(
            when {
                state.isSelecting -> BrowserEvent.ClearSelection
                state.searchQuery != null -> BrowserEvent.ToggleSearch
                else -> BrowserEvent.NavigateUp
            },
        )
    }

    val panelDropTarget = remember(viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                state.currentPath?.let { onEvent(BrowserEvent.DropItems(it)) }
                return true
            }
        }
    }
    var panelModifier = modifier
    if (dragAndDropEnabled) {
        panelModifier = panelModifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.isOurDrag() },
            target = panelDropTarget,
        )
    }

    Scaffold(
        modifier = panelModifier,
        topBar = {
            if (state.isSelecting) {
                val selected = state.selectedItems
                SelectionActionBar(
                    selectedCount = selected.size,
                    onClearSelection = { onEvent(BrowserEvent.ClearSelection) },
                    onCopy = { onEvent(BrowserEvent.Copy(selected)) },
                    onCut = { onEvent(BrowserEvent.Cut(selected)) },
                    onDelete = { onEvent(BrowserEvent.RequestDelete(selected)) },
                    onShare = { onEvent(BrowserEvent.Share(selected)) },
                    onRename = { selected.singleOrNull()?.let { onEvent(BrowserEvent.RequestRename(it)) } },
                    onProperties = { selected.singleOrNull()?.let { onEvent(BrowserEvent.ShowProperties(it)) } },
                    windowInsets = topInsets,
                )
            } else {
                BrowserTopBar(
                    segments = state.breadcrumb,
                    sortOrder = state.sortOrder,
                    onMenuClick = onOpenDrawer,
                    onSegmentClick = { onEvent(BrowserEvent.Navigate(it.path)) },
                    searchQuery = state.searchQuery,
                    onSearchClick = { onEvent(BrowserEvent.ToggleSearch) },
                    onSearchQueryChange = { onEvent(BrowserEvent.SetSearchQuery(it)) },
                    onNewFolder = { onEvent(BrowserEvent.RequestNewFolder) },
                    onNewFile = { onEvent(BrowserEvent.RequestNewFile) },
                    onSortSelected = { onEvent(BrowserEvent.SortBy(it)) },
                    onSelectAll = { onEvent(BrowserEvent.SelectAll) },
                    windowInsets = topInsets,
                )
            }
        },
        bottomBar = {
            DockedPasteBar(
                clipboard = clipboard,
                onPaste = { onEvent(BrowserEvent.Paste) },
                onCancel = { onEvent(BrowserEvent.CancelClipboard) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onEvent(BrowserEvent.Refresh) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.errorMessage != null -> ErrorMessage(state.errorMessage)
                state.visibleItems.isEmpty() && !state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(stringResource(R.string.folder_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> FileList(
                    items = state.visibleItems,
                    selectedPaths = state.selectedPaths,
                    isSelecting = state.isSelecting,
                    dragAndDropEnabled = dragAndDropEnabled,
                    onItemClick = { onEvent(BrowserEvent.OpenItem(it)) },
                    onToggleSelection = { onEvent(BrowserEvent.ToggleSelection(it)) },
                    onAction = { action, item -> onEvent(action.toEvent(item)) },
                    onDragStart = { onEvent(BrowserEvent.StartDrag(it)) },
                    onDropInside = { onEvent(BrowserEvent.DropItems(it)) },
                    contentPadding = if (clipboard == null) {
                        WindowInsets.navigationBars.asPaddingValues()
                    } else {
                        PaddingValues()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        BrowserDialog.NewFolder -> NameInputDialog(
            title = R.string.action_new_folder,
            initialName = "",
            onConfirm = { onEvent(BrowserEvent.ConfirmName(it)) },
            onDismiss = { onEvent(BrowserEvent.DismissDialog) },
        )
        BrowserDialog.NewFile -> NameInputDialog(
            title = R.string.action_new_file,
            initialName = "",
            onConfirm = { onEvent(BrowserEvent.ConfirmName(it)) },
            onDismiss = { onEvent(BrowserEvent.DismissDialog) },
        )
        is BrowserDialog.Rename -> NameInputDialog(
            title = R.string.action_rename,
            initialName = dialog.item.name,
            onConfirm = { onEvent(BrowserEvent.ConfirmName(it)) },
            onDismiss = { onEvent(BrowserEvent.DismissDialog) },
        )
        is BrowserDialog.ConfirmDelete -> ConfirmDeleteDialog(
            items = dialog.items,
            onConfirm = { onEvent(BrowserEvent.ConfirmDelete) },
            onDismiss = { onEvent(BrowserEvent.DismissDialog) },
        )
        is BrowserDialog.Properties -> PropertiesBottomSheet(
            item = dialog.item,
            hashState = dialog.hash,
            onComputeHash = { onEvent(BrowserEvent.ComputeHash) },
            onDismiss = { onEvent(BrowserEvent.DismissDialog) },
        )
    }
}

@Composable
private fun ErrorMessage(message: String?) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            text = if (message == BrowserViewModel.NO_STORAGE) {
                stringResource(R.string.error_no_storage)
            } else {
                stringResource(R.string.error_load_folder, message.orEmpty())
            },
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun FileItemAction.toEvent(item: FileItem): BrowserEvent = when (this) {
    FileItemAction.OPEN_WITH -> BrowserEvent.OpenWith(item)
    FileItemAction.COPY -> BrowserEvent.Copy(listOf(item))
    FileItemAction.CUT -> BrowserEvent.Cut(listOf(item))
    FileItemAction.RENAME -> BrowserEvent.RequestRename(item)
    FileItemAction.DELETE -> BrowserEvent.RequestDelete(listOf(item))
    FileItemAction.PROPERTIES -> BrowserEvent.ShowProperties(item)
}
