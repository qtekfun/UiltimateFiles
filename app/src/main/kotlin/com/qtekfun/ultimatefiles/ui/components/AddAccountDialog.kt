package com.qtekfun.ultimatefiles.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.qtekfun.ultimatefiles.data.network.InvalidKeyFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.qtekfun.ultimatefiles.data.network.HostKeyChangedException
import com.qtekfun.ultimatefiles.data.network.InsecureServerException
import com.qtekfun.ultimatefiles.data.network.UntrustedHostKeyException
import com.qtekfun.ultimatefiles.data.network.InvalidServerUrlException
import com.qtekfun.ultimatefiles.data.network.TrustChoice
import com.qtekfun.ultimatefiles.data.network.UntrustedCertificateException
import com.qtekfun.ultimatefiles.data.network.LoginFlowExpiredException
import com.qtekfun.ultimatefiles.data.network.WebDavException

/**
 * Asks for the address of a Nextcloud server and signs in with its Login Flow: the approval happens in the browser
 * and the app receives an app password, so no password is ever typed here.
 */
@Composable
fun AddAccountDialog(
    onConnect: (
        serverUrl: String,
        label: String,
        trust: TrustChoice,
        openBrowser: (String) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ) -> Unit,
    onConnectSftp: (
        host: String,
        port: Int,
        username: String,
        password: String,
        label: String,
        pinnedFingerprint: String?,
        onDone: (Result<Unit>) -> Unit,
    ) -> Unit,
    onConnectSmb: (
        host: String,
        port: Int,
        share: String,
        domain: String,
        username: String,
        password: String,
        label: String,
        onDone: (Result<Unit>) -> Unit,
    ) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(AccountKind.NEXTCLOUD) }
    val sftp = kind == AccountKind.SFTP
    val smb = kind == AccountKind.SMB
    var keyPem by remember { mutableStateOf<String?>(null) }
    var keyName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(MAX_KEY_BYTES).toByteArray().decodeToString() }
                    }.getOrNull()
                }
                if (text != null && text.trimStart().startsWith("-----BEGIN")) {
                    keyPem = text
                    keyName = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
                    error = null
                } else {
                    error = InvalidKeyFileException()
                }
            }
        }
    }
    var share by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hostKey by remember { mutableStateOf<UntrustedHostKeyException?>(null) }
    var server by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var prompt by remember { mutableStateOf<TrustPrompt?>(null) }
    val uriHandler = LocalUriHandler.current

    fun connect(choice: TrustChoice) {
        busy = true
        error = null
        onConnect(server, label, choice, { url -> uriHandler.openUri(url) }) { result ->
            busy = false
            result.fold(
                onSuccess = { onDismiss() },
                onFailure = { failure ->
                    when (failure) {
                        // Not errors: the user is asked, and the same attempt continues with their answer.
                        is UntrustedCertificateException -> prompt = TrustPrompt.Certificate(failure.info, choice)
                        is InsecureServerException -> prompt = TrustPrompt.Insecure(choice)
                        else -> error = failure
                    }
                },
            )
        }
    }

    fun connectSftp(pinned: String?) {
        busy = true
        error = null
        // A key travels as the account's secret: the key text, then the passphrase after a NUL character.
        val secret = keyPem?.let { if (password.isEmpty()) it else it + "\u0000" + password } ?: password
        onConnectSftp(host, port.toIntOrNull() ?: 0, user, secret, label, pinned) { result ->
            busy = false
            result.fold(
                onSuccess = { onDismiss() },
                onFailure = { failure ->
                    if (failure is UntrustedHostKeyException) hostKey = failure else error = failure
                },
            )
        }
    }

    fun connectSmb() {
        busy = true
        error = null
        onConnectSmb(host, port.toIntOrNull() ?: 0, share, domain, user, password, label) { result ->
            busy = false
            result.fold(onSuccess = { onDismiss() }, onFailure = { error = it })
        }
    }

    hostKey?.let { asked ->
        HostKeyPromptDialog(
            host = host,
            fingerprint = asked.fingerprint,
            keyType = asked.keyType,
            onAccept = {
                hostKey = null
                connectSftp(asked.fingerprint)
            },
            onDismiss = { hostKey = null },
        )
    }

    prompt?.let { asked ->
        TrustPromptDialog(
            prompt = asked,
            onAccept = { choice ->
                prompt = null
                connect(choice)
            },
            onDismiss = { prompt = null },
        )
    }

    fun close() {
        if (busy) onCancel()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == AccountKind.NEXTCLOUD, enabled = !busy, onClick = { kind = AccountKind.NEXTCLOUD }, label = { Text(stringResource(R.string.account_type_nextcloud)) })
                    FilterChip(selected = sftp, enabled = !busy, onClick = { kind = AccountKind.SFTP; port = "22" }, label = { Text(stringResource(R.string.account_type_sftp)) })
                    FilterChip(selected = smb, enabled = !busy, onClick = { kind = AccountKind.SMB; port = "445" }, label = { Text(stringResource(R.string.account_type_smb)) })
                }
                if (sftp || smb) {
                    Text(
                        stringResource(if (smb) R.string.account_smb_hint else R.string.account_sftp_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.account_host)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.account_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (smb) {
                        OutlinedTextField(
                            value = share,
                            onValueChange = { share = it },
                            enabled = !busy,
                            label = { Text(stringResource(R.string.account_share)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            enabled = !busy,
                            label = { Text(stringResource(R.string.account_domain)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.account_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        enabled = !busy,
                        label = { Text(stringResource(if (sftp && keyPem != null) R.string.account_key_passphrase else R.string.account_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (sftp) {
                        TextButton(enabled = !busy, onClick = { pickKey.launch(arrayOf("*/*")) }) {
                            Text(
                                if (keyPem == null) stringResource(R.string.account_key_pick) else stringResource(R.string.account_key_loaded, keyName),
                            )
                        }
                    }
                } else {
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
                }
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
                    if (kind == AccountKind.NEXTCLOUD) Text(stringResource(R.string.account_waiting), style = MaterialTheme.typography.bodyMedium)
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && when (kind) {
                    AccountKind.NEXTCLOUD -> server.isNotBlank()
                    AccountKind.SFTP -> host.isNotBlank() && user.isNotBlank() && (password.isNotEmpty() || keyPem != null)
                    AccountKind.SMB -> host.isNotBlank() && share.isNotBlank() && user.isNotBlank() && password.isNotEmpty()
                },
                onClick = {
                    when (kind) {
                        AccountKind.NEXTCLOUD -> connect(TrustChoice())
                        AccountKind.SFTP -> connectSftp(null)
                        AccountKind.SMB -> connectSmb()
                    }
                },
            ) { Text(stringResource(R.string.account_connect)) }
        },
        dismissButton = {
            TextButton(onClick = ::close) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private enum class AccountKind { NEXTCLOUD, SFTP, SMB }

private fun Throwable.messageRes(): Int = when {
    this is InvalidKeyFileException -> R.string.account_error_key
    message?.contains("LOGON_FAILURE") == true || message?.contains("ACCESS_DENIED") == true -> R.string.account_error_auth
    message?.contains("BAD_NETWORK_NAME") == true -> R.string.account_error_share
    this is InvalidServerUrlException -> R.string.account_error_url
    this is LoginFlowExpiredException -> R.string.account_error_expired
    this is javax.net.ssl.SSLException -> R.string.account_error_certificate
    this is HostKeyChangedException -> R.string.account_error_hostkey
    this is net.schmizz.sshj.userauth.UserAuthException -> R.string.account_error_auth
    this is ActivityNotFoundException -> R.string.account_error_browser
    this is WebDavException && code == 401 -> R.string.account_error_auth
    this is WebDavException && code == 404 -> R.string.account_error_path
    else -> R.string.account_error_generic
}

private const val MAX_KEY_BYTES = 64 * 1024
