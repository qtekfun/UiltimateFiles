package com.qtekfun.ultimatefiles.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.data.network.CertificateInfo
import com.qtekfun.ultimatefiles.data.network.PinnedTls
import com.qtekfun.ultimatefiles.data.network.TrustChoice
import java.text.DateFormat
import java.util.Date

/** A question the server connection raised that only the user can answer. */
sealed interface TrustPrompt {
    /** The certificate is not signed by anything the device trusts; pinning it is the user's call. */
    data class Certificate(val info: CertificateInfo, val choice: TrustChoice) : TrustPrompt

    /** The address is plain `http://`. */
    data class Insecure(val choice: TrustChoice) : TrustPrompt
}

@Composable
fun TrustPromptDialog(prompt: TrustPrompt, onAccept: (TrustChoice) -> Unit, onDismiss: () -> Unit) {
    when (prompt) {
        is TrustPrompt.Certificate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.trust_certificate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.trust_certificate_message))
                    Text(stringResource(R.string.trust_certificate_subject, prompt.info.subject), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            R.string.trust_certificate_expires,
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(prompt.info.notAfterMillis)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(R.string.trust_certificate_fingerprint), style = MaterialTheme.typography.labelMedium)
                    Text(PinnedTls.display(prompt.info.sha256), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { onAccept(prompt.choice.copy(pinnedSha256 = prompt.info.sha256)) }) {
                    Text(stringResource(R.string.trust_certificate_accept))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is TrustPrompt.Insecure -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.trust_insecure_title)) },
            text = { Text(stringResource(R.string.trust_insecure_message)) },
            confirmButton = {
                TextButton(onClick = { onAccept(prompt.choice.copy(allowInsecureHttp = true)) }) {
                    Text(stringResource(R.string.trust_insecure_accept))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The SSH server's host key is new: showing its fingerprint lets the user compare it with what the server reports. */
@Composable
fun HostKeyPromptDialog(host: String, fingerprint: String, keyType: String, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trust_hostkey_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trust_hostkey_message, host))
                Text(stringResource(R.string.trust_hostkey_type, keyType), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.trust_certificate_fingerprint), style = MaterialTheme.typography.labelMedium)
                Text(fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.trust_certificate_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
