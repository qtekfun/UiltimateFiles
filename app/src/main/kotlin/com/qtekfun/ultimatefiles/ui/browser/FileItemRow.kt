package com.qtekfun.ultimatefiles.ui.browser

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.domain.usecase.ArchiveFormat
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import com.qtekfun.ultimatefiles.core.util.FileKind
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.core.util.kind
import com.qtekfun.ultimatefiles.ui.dualpanel.DragDropState
import java.text.DateFormat
import java.util.Date

/** Quick actions offered by the per-row menu (long press or the three-dot button). */
enum class FileItemAction { OPEN_WITH, COPY, CUT, EXTRACT, COMPRESS, RENAME, DELETE, PROPERTIES }

/** Whether [this] makes sense for [item]: archives are only extracted outside archives, and nothing is packed from inside one. */
internal fun FileItemAction.appliesTo(item: FileItem): Boolean {
    val insideArchive = ArchivePaths.isArchivePath(item.path)
    return when (this) {
        FileItemAction.OPEN_WITH -> !item.isDirectory
        FileItemAction.EXTRACT -> !item.isDirectory && !insideArchive && ArchiveFormat.of(item.name) != null
        FileItemAction.COMPRESS -> !insideArchive
        else -> true
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
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
    var rowModifier = modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    if (dragAndDropEnabled && item.isDirectory) {
        rowModifier = rowModifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.isOurDrag() },
            target = dropTarget,
        )
    }

    var iconModifier = Modifier.size(40.dp)
    if (dragAndDropEnabled) {
        iconModifier = iconModifier.dragAndDropSource { _ ->
            onDragStart()
            DragAndDropTransferData(ClipData.newPlainText(DragDropState.CLIP_LABEL, item.name))
        }
    }

    ListItem(
        modifier = rowModifier,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        leadingContent = {
            Box(
                modifier = iconModifier.clickable(onClick = onToggleSelection),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Check else item.kind().icon(),
                    contentDescription = null,
                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    FileItemAction.entries
                        .filter { it.appliesTo(item) }
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
        },
    )
}

/** True when the drag was started by one of our rows (as opposed to another app). */
@OptIn(ExperimentalFoundationApi::class)
fun DragAndDropEvent.isOurDrag(): Boolean =
    toAndroidDragEvent().clipDescription?.label?.toString() == DragDropState.CLIP_LABEL

internal fun FileItemAction.labelRes(): Int = when (this) {
    FileItemAction.OPEN_WITH -> R.string.action_open_with
    FileItemAction.COPY -> R.string.action_copy
    FileItemAction.CUT -> R.string.action_cut
    FileItemAction.EXTRACT -> R.string.action_extract
    FileItemAction.COMPRESS -> R.string.action_compress
    FileItemAction.RENAME -> R.string.action_rename
    FileItemAction.DELETE -> R.string.action_delete
    FileItemAction.PROPERTIES -> R.string.action_properties
}

internal fun FileKind.icon(): ImageVector = when (this) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.TEXT -> Icons.Filled.Description
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.APK -> Icons.Filled.Android
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun FileItem.summary(): String {
    val date = if (lastModifiedMillis > 0) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastModifiedMillis))
    } else {
        ""
    }
    return if (isDirectory) date else listOf(formatBytes(sizeBytes), date).filter { it.isNotEmpty() }.joinToString(" · ")
}
