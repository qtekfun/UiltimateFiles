package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.core.util.formatBytes
import com.qtekfun.ultimatefiles.core.util.formatDuration

/**
 * In-app mirror of the foreground-service notification, docked at the bottom of the screen: the file name and the
 * figures on one line, the progress bar, and the two buttons on a line of their own so that neither a long name nor
 * longer figures can push them onto several lines.
 */
@Composable
fun TransferProgressBar(
    progress: TransferProgress?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (progress == null || progress.status !in ACTIVE) return
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = progress.currentName.ifEmpty { stringResource(R.string.transfer_preparing) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val remaining = progress.remainingSeconds?.let { stringResource(R.string.transfer_remaining, formatDuration(it)) }
                val verifying = if (progress.status == TransferStatus.VERIFYING) stringResource(R.string.transfer_verifying) else null
                Text(
                    text = if (paused) {
                        stringResource(R.string.transfer_paused)
                    } else {
                        listOfNotNull(
                            verifying,
                            stringResource(R.string.transfer_speed, formatBytes(progress.bytesPerSecond)),
                            remaining,
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The figures change all the time; capping them keeps the name from being squeezed out.
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }
            val fraction = progress.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onTogglePause, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (paused) R.string.transfer_resume else R.string.transfer_pause), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.transfer_cancel), maxLines = 1)
                }
            }
        }
    }
}

private val ACTIVE = setOf(
    TransferStatus.RUNNING,
    TransferStatus.VERIFYING,
    TransferStatus.PENDING,
    TransferStatus.WAITING_CONFLICT,
)
