package com.qtekfun.ultimatefiles.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R

const val MIN_PASSPHRASE_LENGTH = 8

/** Choose what goes into the backup and, when accounts are included, the passphrase that protects their passwords. */
@Composable
fun ExportBackupDialog(
    accountCount: Int,
    onChoose: (includeAccounts: Boolean, passphrase: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var include by remember { mutableStateOf(accountCount > 0) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val needsPassphrase = include && accountCount > 0
    val tooShort = needsPassphrase && passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = needsPassphrase && confirm.isNotEmpty() && confirm != passphrase
    val valid = !needsPassphrase || (passphrase.length >= MIN_PASSPHRASE_LENGTH && confirm == passphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_export_hint), style = MaterialTheme.typography.bodySmall)
                if (accountCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = include, onCheckedChange = { include = it })
                        Text(pluralStringResource(R.plurals.backup_include_accounts, accountCount, accountCount))
                    }
                }
                if (needsPassphrase) {
                    Text(stringResource(R.string.backup_passphrase_hint), style = MaterialTheme.typography.bodySmall)
                    PassphraseField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = stringResource(R.string.backup_passphrase),
                        error = if (tooShort) stringResource(R.string.backup_passphrase_short, MIN_PASSPHRASE_LENGTH) else null,
                    )
                    PassphraseField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = stringResource(R.string.backup_passphrase_confirm),
                        error = if (mismatch) stringResource(R.string.backup_passphrase_mismatch) else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onChoose(include, passphrase.takeIf { needsPassphrase }) },
            ) { Text(stringResource(R.string.backup_choose_location)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Asks for the passphrase of a backup that contains accounts; [wrong] marks a failed attempt. */
@Composable
fun ImportPassphraseDialog(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_import_passphrase_message), style = MaterialTheme.typography.bodySmall)
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase),
                    error = if (wrong) stringResource(R.string.backup_wrong_passphrase) else null,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = passphrase.isNotEmpty(), onClick = { onSubmit(passphrase) }) {
                Text(stringResource(R.string.backup_import_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One-shot result message for an export or import. */
@Composable
fun BackupResultDialog(outcome: BackupOutcome, onDismiss: () -> Unit) {
    val message = when (outcome) {
        BackupOutcome.Exported -> stringResource(R.string.backup_done_export)
        is BackupOutcome.Imported -> stringResource(
            R.string.backup_done_import,
            pluralStringResource(R.plurals.backup_accounts_restored, outcome.accounts, outcome.accounts),
        )
        BackupOutcome.InvalidFile -> stringResource(R.string.backup_invalid)
        BackupOutcome.UnsupportedVersion -> stringResource(R.string.backup_unsupported)
        is BackupOutcome.Failed -> stringResource(R.string.backup_failed, outcome.reason.orEmpty())
        is BackupOutcome.NeedsPassphrase -> return // handled by ImportPassphraseDialog
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun PassphraseField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
