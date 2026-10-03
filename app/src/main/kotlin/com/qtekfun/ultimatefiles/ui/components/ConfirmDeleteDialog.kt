package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.FileItem

@Composable
fun ConfirmDeleteDialog(items: List<FileItem>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Text(
                if (items.size == 1) {
                    stringResource(R.string.delete_message_single, items.first().name)
                } else {
                    stringResource(R.string.delete_message_multiple, items.size)
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
