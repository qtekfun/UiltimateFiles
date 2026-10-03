package com.qtekfun.fexplo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.HashState
import com.qtekfun.fexplo.core.util.formatBytes
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Bottom sheet with the file metadata and an on-demand MD5 / SHA-256 calculation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesBottomSheet(
    item: FileItem,
    hashState: HashState,
    onComputeHash: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge)
            PropertyRow(stringResource(R.string.property_path), item.path)
            PropertyRow(
                stringResource(R.string.property_type),
                if (item.isDirectory) stringResource(R.string.property_type_folder) else item.mimeType.orEmpty()
                    .ifEmpty { stringResource(R.string.property_unknown) },
            )
            if (!item.isDirectory) {
                PropertyRow(
                    stringResource(R.string.property_size),
                    stringResource(
                        R.string.property_size_value,
                        formatBytes(item.sizeBytes),
                        NumberFormat.getInstance().format(item.sizeBytes),
                    ),
                )
            }
            PropertyRow(stringResource(R.string.property_modified), formatModified(item))
            PropertyRow(stringResource(R.string.property_attributes), attributes(item))
            if (!item.isDirectory) HashSection(hashState, onComputeHash)
        }
    }
}

@Composable
private fun HashSection(state: HashState, onComputeHash: () -> Unit) {
    when (state) {
        HashState.Idle, is HashState.Failed -> {
            if (state is HashState.Failed) {
                Text(
                    text = stringResource(R.string.hash_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onComputeHash, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hash_calculate))
            }
        }
        HashState.Computing -> {
            Text(stringResource(R.string.hash_computing), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is HashState.Done -> {
            PropertyRow(stringResource(R.string.hash_md5), state.hashes.md5, monospace = true)
            PropertyRow(stringResource(R.string.hash_sha256), state.hashes.sha256, monospace = true)
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
private fun attributes(item: FileItem): String {
    val access = stringResource(if (item.isWritable) R.string.property_read_write else R.string.property_read_only)
    return if (item.isHidden) "$access, ${stringResource(R.string.property_hidden)}" else access
}

@Composable
private fun formatModified(item: FileItem): String =
    if (item.lastModifiedMillis <= 0) {
        stringResource(R.string.property_unknown)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.lastModifiedMillis))
    }
