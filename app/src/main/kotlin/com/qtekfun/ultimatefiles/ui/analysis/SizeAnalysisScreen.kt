package com.qtekfun.ultimatefiles.ui.analysis

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.SizeNode
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.ui.browser.FileLeading
import com.qtekfun.ultimatefiles.ui.components.PlaceholderTone
import com.qtekfun.ultimatefiles.ui.components.StatePlaceholder
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Where the space went: the folder that was analysed, its entries from the biggest down with their share of it, and a
 * way down into each folder. A long press on an entry offers to see it (or open it) in a panel, where the usual actions
 * (copy, move, delete) apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeAnalysisScreen(
    state: SizeAnalysisState,
    onBack: () -> Unit,
    onOpen: (SizeNode) -> Unit,
    onCancel: () -> Unit,
    onRetry: (path: String, label: String) -> Unit,
    onShowInPanel: (FileItem) -> Unit,
    onOpenInPanel: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = when (state) {
        SizeAnalysisState.Idle -> ""
        is SizeAnalysisState.Scanning -> state.label
        is SizeAnalysisState.Failed -> state.label
        is SizeAnalysisState.Done -> if (state.trail.size == 1) state.label else state.current.item.name
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                SizeAnalysisState.Idle -> Unit
                is SizeAnalysisState.Scanning -> Scanning(state, onCancel)
                is SizeAnalysisState.Failed -> StatePlaceholder(
                    icon = Icons.Filled.ErrorOutline,
                    title = stringResource(R.string.analysis_failed_title),
                    message = state.message,
                    tone = PlaceholderTone.ERROR,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { onRetry(state.path, state.label) },
                )
                is SizeAnalysisState.Done -> Result(state, onOpen, onShowInPanel, onOpenInPanel)
            }
        }
    }
}

@Composable
private fun Scanning(state: SizeAnalysisState.Scanning, onCancel: () -> Unit) {
    val progress = state.progress
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.analysis_scanning), style = MaterialTheme.typography.titleMedium)
        if (progress != null) {
            Text(
                text = stringResource(R.string.analysis_progress, filesText(progress.files), formatBytes(progress.bytes)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = progress.currentFolder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun Result(
    state: SizeAnalysisState.Done,
    onOpen: (SizeNode) -> Unit,
    onShowInPanel: (FileItem) -> Unit,
    onOpenInPanel: (FileItem) -> Unit,
) {
    val current = state.current
    val children = current.children.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "summary") { Summary(current, unreadable = state.analysis.unreadable.takeIf { state.trail.size == 1 } ?: 0) }
        if (children.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    StatePlaceholder(
                        icon = Icons.Filled.FolderOpen,
                        title = stringResource(R.string.folder_empty),
                    )
                }
            }
        }
        items(children, key = { it.item.path }) { node ->
            ResultRow(node, total = current.bytes, onOpen = onOpen, onShowInPanel = onShowInPanel, onOpenInPanel = onOpenInPanel)
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Summary(node: SizeNode, unreadable: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatBytes(node.bytes), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = filesText(node.files),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (unreadable > 0) {
                Text(
                    text = LocalResources.current.getQuantityString(R.plurals.analysis_unreadable, unreadable, unreadable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    node: SizeNode,
    total: Long,
    onOpen: (SizeNode) -> Unit,
    onShowInPanel: (FileItem) -> Unit,
    onOpenInPanel: (FileItem) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val fraction = if (total > 0) (node.bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).roundToInt()
    val percentText = if (percent == 0 && node.bytes > 0) stringResource(R.string.analysis_less_than_one) else "$percent%"
    val details = if (node.item.isDirectory || node.isOther) {
        stringResource(R.string.analysis_row_details, percentText, filesText(node.files))
    } else {
        percentText
    }
    val canOpen = node.item.isDirectory && !node.isOther && node.children != null
    Box {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = if (menuOpen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
            leadingContent = { if (node.isOther) OtherTile() else FileLeading(node.item, selected = false) },
            headlineContent = {
                Text(
                    text = if (node.isOther) {
                        LocalResources.current.getQuantityString(R.plurals.analysis_other, node.folded, node.folded)
                    } else {
                        node.item.name
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            },
            trailingContent = { Text(formatBytes(node.bytes), style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { if (canOpen) onOpen(node) },
                    onLongClick = { if (!node.isOther) menuOpen = true },
                ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.analysis_show_in_panel)) },
                onClick = {
                    menuOpen = false
                    onShowInPanel(node.item)
                },
            )
            if (node.item.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.analysis_open_in_panel)) },
                    onClick = {
                        menuOpen = false
                        onOpenInPanel(node.item)
                    },
                )
            }
        }
    }
}

@Composable
private fun OtherTile() {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {}
            Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun filesText(count: Int): String =
    LocalResources.current.getQuantityString(R.plurals.analysis_files, count, NumberFormat.getIntegerInstance().format(count))
