package com.qtekfun.fexplo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.TransferProgress
import com.qtekfun.fexplo.core.model.TransferStatus
import com.qtekfun.fexplo.core.util.formatBytes
import com.qtekfun.fexplo.core.util.formatDuration

/** Slim in-app mirror of the foreground-service notification, docked at the bottom of the screen. */
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                )
                TextButton(onClick = onTogglePause) {
                    Text(stringResource(if (paused) R.string.transfer_resume else R.string.transfer_pause))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.transfer_cancel)) }
            }
            val fraction = progress.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
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
