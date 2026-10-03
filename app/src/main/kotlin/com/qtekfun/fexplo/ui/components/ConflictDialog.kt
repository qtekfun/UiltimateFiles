package com.qtekfun.fexplo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.ConflictDecision
import com.qtekfun.fexplo.core.model.ConflictPrompt
import com.qtekfun.fexplo.core.model.ConflictResolution
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.util.formatBytes
import java.text.DateFormat
import java.util.Date

/** Asks how to resolve a name collision: overwrite, skip or keep both, optionally for the whole batch. */
@Composable
fun ConflictDialog(
    prompt: ConflictPrompt,
    onDecision: (ConflictDecision) -> Unit,
    onCancelTransfer: () -> Unit,
) {
    var applyToAll by rememberSaveable { mutableStateOf(false) }
    fun decide(resolution: ConflictResolution) = onDecision(ConflictDecision(resolution, applyToAll))

    AlertDialog(
        onDismissRequest = onCancelTransfer,
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.conflict_message, prompt.existing.name))
                Text(
                    text = stringResource(R.string.conflict_source, formatSize(prompt.source), formatDate(prompt.source)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.conflict_existing,
                        formatSize(prompt.existing),
                        formatDate(prompt.existing),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { applyToAll = !applyToAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Text(stringResource(R.string.conflict_apply_to_all))
                }
                OutlinedButton(
                    onClick = { decide(ConflictResolution.OVERWRITE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_overwrite)) }
                OutlinedButton(
                    onClick = { decide(ConflictResolution.SKIP) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_skip)) }
                OutlinedButton(
                    onClick = { decide(ConflictResolution.RENAME) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conflict_rename)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancelTransfer) { Text(stringResource(R.string.conflict_cancel_transfer)) }
        },
    )
}

@Composable
private fun formatSize(item: FileItem): String =
    if (item.isDirectory) stringResource(R.string.property_type_folder) else formatBytes(item.sizeBytes)

private fun formatDate(item: FileItem): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.lastModifiedMillis))
