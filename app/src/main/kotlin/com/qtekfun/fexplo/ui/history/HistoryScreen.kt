package com.qtekfun.fexplo.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.core.util.formatBytes
import com.qtekfun.fexplo.domain.history.HistoryEntry
import com.qtekfun.fexplo.domain.history.HistoryOperation
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
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
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
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
