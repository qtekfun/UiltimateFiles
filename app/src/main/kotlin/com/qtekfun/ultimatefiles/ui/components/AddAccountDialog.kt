package com.qtekfun.ultimatefiles.ui.components

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.data.network.InvalidServerUrlException
import com.qtekfun.ultimatefiles.data.network.LoginFlowExpiredException
import com.qtekfun.ultimatefiles.data.network.WebDavException

/**
 * Asks for the address of a Nextcloud server and signs in with its Login Flow: the approval happens in the browser
 * and the app receives an app password, so no password is ever typed here.
 */
@Composable
fun AddAccountDialog(
    onConnect: (serverUrl: String, label: String, openBrowser: (String) -> Unit, onDone: (Result<Unit>) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var server by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val uriHandler = LocalUriHandler.current

    fun close() {
        if (busy) onCancel()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_server)) },
                    placeholder = { Text("https://cloud.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = stringResource(it.messageRes(), it.message.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (busy) {
                    Text(stringResource(R.string.account_waiting), style = MaterialTheme.typography.bodyMedium)
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && server.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    onConnect(
                        server,
                        label,
                        { url -> uriHandler.openUri(url) },
                    ) { result ->
                        busy = false
                        result.fold(onSuccess = { onDismiss() }, onFailure = { error = it })
                    }
                },
            ) { Text(stringResource(R.string.account_connect)) }
        },
        dismissButton = {
            TextButton(onClick = ::close) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun Throwable.messageRes(): Int = when {
    this is InvalidServerUrlException -> R.string.account_error_url
    this is LoginFlowExpiredException -> R.string.account_error_expired
    this is ActivityNotFoundException -> R.string.account_error_browser
    this is WebDavException && code == 401 -> R.string.account_error_auth
    this is WebDavException && code == 404 -> R.string.account_error_path
    else -> R.string.account_error_generic
}
