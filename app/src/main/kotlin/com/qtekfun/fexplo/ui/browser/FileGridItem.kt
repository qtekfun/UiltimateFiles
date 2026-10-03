package com.qtekfun.fexplo.ui.browser

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.util.kind
import com.qtekfun.fexplo.ui.dualpanel.DragDropState

/** Grid counterpart of [FileItemRow]: same gestures (tap, long-press menu, icon tap selects) and drag and drop. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileItem,
    selected: Boolean,
    dragAndDropEnabled: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onAction: (FileItemAction) -> Unit,
    onDragStart: () -> Unit,
    onDropInside: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dropTarget = remember(item.path) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDropInside(item.path)
                return true
            }
        }
    }

    var cell = modifier
        .padding(4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    if (dragAndDropEnabled && item.isDirectory) {
        cell = cell.dragAndDropTarget(shouldStartDragAndDrop = { it.isOurDrag() }, target = dropTarget)
    }

    var iconModifier = Modifier.size(56.dp)
    if (dragAndDropEnabled) {
        iconModifier = iconModifier.dragAndDropSource { _ ->
            onDragStart()
            DragAndDropTransferData(ClipData.newPlainText(DragDropState.CLIP_LABEL, item.name))
        }
    }

    Box(cell) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(iconModifier.clickable(onClick = onToggleSelection), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Check else item.kind().icon(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            FileItemAction.entries
                .filter { it != FileItemAction.OPEN_WITH || !item.isDirectory }
                .forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.labelRes())) },
                        onClick = {
                            menuOpen = false
                            onAction(action)
                        },
                    )
                }
        }
    }
}
