package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferSummary

/** Offered at start-up when the system stopped the app in the middle of a copy or move. */
@Composable
fun InterruptedTransfersDialog(tasks: List<TransferSummary>, onResume: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.interrupted_title)) },
        text = {
            Column {
                Text(stringResource(R.string.interrupted_message))
                tasks.take(MAX_LISTED).forEach { task ->
                    val one = task.itemCount == 1
                    val text = when (task.operation) {
                        OperationType.CUT -> if (one) R.string.history_moving_one else R.string.history_moving_many
                        else -> if (one) R.string.history_copying_one else R.string.history_copying_many
                    }
                    Text("• " + stringResource(text, if (one) task.firstItemName else task.itemCount))
                }
                if (tasks.size > MAX_LISTED) {
                    Text(pluralStringResource(R.plurals.interrupted_more, tasks.size - MAX_LISTED, tasks.size - MAX_LISTED))
                }
            }
        },
        confirmButton = { TextButton(onClick = onResume) { Text(stringResource(R.string.interrupted_resume)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.interrupted_discard)) } },
    )
}

private const val MAX_LISTED = 3
