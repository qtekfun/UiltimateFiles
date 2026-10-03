package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ClipboardState
import com.qtekfun.ultimatefiles.core.model.OperationType

/**
 * Bottom bar docked under the file list (use as the Scaffold `bottomBar`, never as a floating
 * element). Renders nothing while the clipboard is empty.
 */
@Composable
fun DockedPasteBar(
    clipboard: ClipboardState?,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (clipboard == null) return
    val summary = when (clipboard.operation) {
        OperationType.COPY -> R.string.clipboard_copy_summary
        OperationType.CUT -> R.string.clipboard_move_summary
    }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(summary, clipboard.items.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onPaste) { Text(stringResource(R.string.action_paste_here)) }
        }
    }
}
