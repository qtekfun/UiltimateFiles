package com.qtekfun.ultimatefiles.ui.browser

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.ViewMode

@Composable
fun FileList(
    items: List<FileItem>,
    viewMode: ViewMode,
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
    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(columns = GridCells.Adaptive(GRID_CELL_MIN_WIDTH), modifier = modifier, contentPadding = contentPadding) {
            gridItems(items, key = { it.path }) { item ->
                FileGridItem(
                    item = item,
                    selected = item.path in selectedPaths,
                    dragAndDropEnabled = dragAndDropEnabled,
                    onClick = { if (isSelecting) onToggleSelection(item) else onItemClick(item) },
                    onToggleSelection = { onToggleSelection(item) },
                    onAction = { onAction(it, item) },
                    onDragStart = { onDragStart(item) },
                    onDropInside = onDropInside,
                )
            }
        }
        return
    }
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

private val GRID_CELL_MIN_WIDTH = 104.dp
