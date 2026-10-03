package com.qtekfun.ultimatefiles.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.model.TransferSummary
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.core.util.formatDuration
import com.qtekfun.ultimatefiles.domain.history.HistoryEntry
import com.qtekfun.ultimatefiles.domain.history.HistoryOperation
import com.qtekfun.ultimatefiles.domain.transfer.TransferState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    running: TransferState,
    onTogglePause: () -> Unit,
    onCancelRunning: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty() && running.active == null && running.queuedTasks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                running.active?.let { active ->
                    item(key = "running") {
                        RunningRow(active, running.progress, running.paused, onTogglePause, onCancelRunning)
                    }
                }
                items(running.queuedTasks) { queued -> QueuedRow(queued) }
                items(entries) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val title = when (entry.operation) {
        HistoryOperation.COPY -> if (entry.itemCount == 1) R.string.history_copied_one else R.string.history_copied_many
        HistoryOperation.MOVE -> if (entry.itemCount == 1) R.string.history_moved_one else R.string.history_moved_many
        HistoryOperation.DELETE -> if (entry.itemCount == 1) R.string.history_deleted_one else R.string.history_deleted_many
        HistoryOperation.COMPRESS -> if (entry.itemCount == 1) R.string.history_compressed_one else R.string.history_compressed_many
        HistoryOperation.EXTRACT -> if (entry.itemCount == 1) R.string.history_extracted_one else R.string.history_extracted_many
    }
    val subject: Any = if (entry.itemCount == 1) entry.firstItemName else entry.itemCount
    val details = listOfNotNull(
        entry.targetName?.let { stringResource(R.string.history_to, it) },
        formatBytes(entry.totalBytes).takeIf { entry.totalBytes > 0 },
        entry.status.label()?.let { stringResource(it) },
        entry.error,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.finishedAtMillis)),
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(stringResource(title, subject)) },
        supportingContent = { Text(details) },
        leadingContent = {
            Icon(
                imageVector = entry.icon(),
                contentDescription = null,
                tint = when (entry.status) {
                    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}

private fun TransferStatus.label(): Int? = when (this) {
    TransferStatus.FAILED -> R.string.history_status_failed
    TransferStatus.CANCELLED -> R.string.history_status_cancelled
    else -> null
}

private fun HistoryEntry.icon(): ImageVector = when (status) {
    TransferStatus.FAILED -> Icons.Filled.Error
    TransferStatus.CANCELLED -> Icons.Filled.RemoveCircle
    else -> if (operation == HistoryOperation.DELETE) Icons.Filled.Delete else Icons.Filled.CheckCircle
}

@Composable
private fun RunningRow(
    task: TransferSummary,
    progress: TransferProgress?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
) {
    val fraction = progress?.fraction
    val speed = progress?.bytesPerSecond?.takeIf { it > 0 && !paused }?.let { formatBytes(it) + "/s" }
    val remaining = progress?.remainingSeconds?.takeIf { !paused }
    val details = listOfNotNull(
        task.targetName?.let { stringResource(R.string.history_to, it) },
        if (paused) stringResource(R.string.history_paused) else speed,
        remaining?.let { stringResource(R.string.transfer_remaining, formatDuration(it)) },
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(task.runningTitle()) },
        supportingContent = {
            Column {
                if (progress != null && progress.currentName.isNotEmpty()) {
                    Text(progress.currentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (details.isNotEmpty()) Text(details)
                if (fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        },
        leadingContent = { Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.history_running)) },
        trailingContent = {
            Row {
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(if (paused) R.string.transfer_resume else R.string.transfer_pause),
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.transfer_cancel))
                }
            }
        },
    )
}

@Composable
private fun QueuedRow(task: TransferSummary) {
    ListItem(
        headlineContent = { Text(task.runningTitle()) },
        supportingContent = { Text(stringResource(R.string.history_queued)) },
        leadingContent = { Icon(Icons.Filled.Schedule, contentDescription = null) },
    )
}

@Composable
private fun TransferSummary.runningTitle(): String {
    val one = itemCount == 1
    val title = when (operation) {
        OperationType.CUT -> if (one) R.string.history_moving_one else R.string.history_moving_many
        OperationType.COMPRESS -> if (one) R.string.history_compressing_one else R.string.history_compressing_many
        OperationType.EXTRACT -> if (one) R.string.history_extracting_one else R.string.history_extracting_many
        else -> if (one) R.string.history_copying_one else R.string.history_copying_many
    }
    return stringResource(title, if (one) firstItemName else itemCount)
}
