package com.qtekfun.fexplo.ui.browser

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.fexplo.core.model.FileItem

@Composable
fun FileList(
    items: List<FileItem>,
    selectedPaths: Set<String>,
    isSelecting: Boolean,
    dragAndDropEnabled: Boolean,
    onItemClick: (FileItem) -> Unit,
    onToggleSelection: (FileItem) -> Unit,
    onAction: (FileItemAction, FileItem) -> Unit,
    onDragStart: (FileItem) -> Unit,
    onDropInside: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(items, key = { it.path }) { item ->
            FileItemRow(
                item = item,
                selected = item.path in selectedPaths,
                dragAndDropEnabled = dragAndDropEnabled,
                // While selecting, a tap toggles the row instead of opening it.
                onClick = { if (isSelecting) onToggleSelection(item) else onItemClick(item) },
                onToggleSelection = { onToggleSelection(item) },
                onAction = { onAction(it, item) },
                onDragStart = { onDragStart(item) },
                onDropInside = onDropInside,
            )
        }
    }
}
