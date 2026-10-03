package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatefiles.R

/** Shown after dropping items on the other panel: copy or move? */
@Composable
fun DropActionDialog(
    itemCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drop_title)) },
        text = { Text(stringResource(R.string.drop_message, itemCount)) },
        confirmButton = {
            TextButton(onClick = onMove) { Text(stringResource(R.string.drop_move)) }
        },
        dismissButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.drop_copy)) }
        },
    )
}
