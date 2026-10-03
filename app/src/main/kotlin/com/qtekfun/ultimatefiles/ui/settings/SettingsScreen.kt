package com.qtekfun.ultimatefiles.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.ui.components.rememberIgnoringBatteryOptimizations
import com.qtekfun.ultimatefiles.ui.components.requestIgnoreBatteryOptimizations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    accountCount: Int,
    outcome: BackupOutcome?,
    onExport: (uri: Uri, includeAccounts: Boolean, passphrase: String?) -> Unit,
    onImport: (uri: Uri, passphrase: String?) -> Unit,
    onDismissOutcome: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onVerifyCopies: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExport by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val choice = pendingExport
        pendingExport = null
        if (uri != null && choice != null) onExport(uri, choice.first, choice.second)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri, null)
    }

    if (showExport) {
        ExportBackupDialog(
            accountCount = accountCount,
            onChoose = { include, passphrase ->
                showExport = false
                pendingExport = include to passphrase
                exportLauncher.launch(BACKUP_FILE_NAME)
            },
            onDismiss = { showExport = false },
        )
    }
    when (outcome) {
        null -> Unit
        is BackupOutcome.NeedsPassphrase -> ImportPassphraseDialog(
            wrong = outcome.wrong,
            onSubmit = { onImport(outcome.uri, it) },
            onDismiss = onDismissOutcome,
        )
        else -> BackupResultDialog(outcome, onDismissOutcome)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val description = mode.descriptionRes()
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = preferences.themeMode == mode,
                            role = Role.RadioButton,
                            onClick = { onThemeMode(mode) },
                        ),
                        headlineContent = { Text(stringResource(mode.labelRes())) },
                        supportingContent = if (description != null) {
                            { Text(stringResource(description)) }
                        } else {
                            null
                        },
                        leadingContent = { RadioButton(selected = preferences.themeMode == mode, onClick = null) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_transfers),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_verify_copies)) },
                supportingContent = { Text(stringResource(R.string.settings_verify_copies_summary)) },
                trailingContent = { Switch(checked = preferences.verifyCopies, onCheckedChange = null) },
                modifier = Modifier.selectable(
                    selected = preferences.verifyCopies,
                    role = Role.Switch,
                    onClick = { onVerifyCopies(!preferences.verifyCopies) },
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                    trailingContent = { Switch(checked = preferences.dynamicColor, onCheckedChange = null) },
                    modifier = Modifier.selectable(
                        selected = preferences.dynamicColor,
                        role = Role.Switch,
                        onClick = { onDynamicColor(!preferences.dynamicColor) },
                    ),
                )
            }
            val batteryExempt by rememberIgnoringBatteryOptimizations()
            val context = LocalContext.current
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_battery)) },
                supportingContent = {
                    Text(stringResource(if (batteryExempt) R.string.settings_battery_on else R.string.settings_battery_off))
                },
                trailingContent = { Switch(checked = batteryExempt, onCheckedChange = null) },
                modifier = Modifier.clickable { requestIgnoreBatteryOptimizations(context) },
            )
            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
                modifier = Modifier.clickable { showExport = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.AMOLED -> R.string.theme_amoled
}

private fun ThemeMode.descriptionRes(): Int? = when (this) {
    ThemeMode.AMOLED -> R.string.theme_amoled_summary
    else -> null
}

private const val BACKUP_FILE_NAME = "ultimatefiles-backup.json"
