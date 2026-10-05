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
import androidx.compose.material3.TextButton
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
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.ui.components.CheckNotice
import com.qtekfun.ultimatefiles.ui.components.aggressiveBatteryVendor
import com.qtekfun.ultimatefiles.ui.components.areNotificationsAllowed
import com.qtekfun.ultimatefiles.ui.components.isIgnoringBatteryOptimizations
import com.qtekfun.ultimatefiles.ui.components.isProgressChannelUsable
import com.qtekfun.ultimatefiles.ui.components.openAppDetails
import com.qtekfun.ultimatefiles.ui.components.openAppNotificationSettings
import com.qtekfun.ultimatefiles.ui.components.rememberCheck
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
    onThumbnailsOnNetwork: (Boolean) -> Unit,
    onPanelBarPosition: (PanelBarPosition) -> Unit,
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
                text = stringResource(R.string.settings_panels),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(Modifier.selectableGroup()) {
                PanelBarPosition.entries.forEach { position ->
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = preferences.panelBarPosition == position,
                            role = Role.RadioButton,
                            onClick = { onPanelBarPosition(position) },
                        ),
                        headlineContent = { Text(stringResource(position.labelRes())) },
                        leadingContent = { RadioButton(selected = preferences.panelBarPosition == position, onClick = null) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_thumbnails),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_thumbnails_network)) },
                supportingContent = { Text(stringResource(R.string.settings_thumbnails_network_summary)) },
                trailingContent = { Switch(checked = preferences.thumbnailsOnNetwork, onCheckedChange = null) },
                modifier = Modifier.selectable(
                    selected = preferences.thumbnailsOnNetwork,
                    role = Role.Switch,
                    onClick = { onThumbnailsOnNetwork(!preferences.thumbnailsOnNetwork) },
                ),
            )
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
            ReliabilitySection()
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

private fun PanelBarPosition.labelRes(): Int = when (this) {
    PanelBarPosition.TOP -> R.string.settings_panel_bar_top
    PanelBarPosition.BOTTOM -> R.string.settings_panel_bar_bottom
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

/**
 * Everything long copies need from the system, checked live: a red notice with a fix button for each thing missing, and
 * for phone makers that kill background apps on their own, the steps their battery manager needs.
 */
@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    val notifications by rememberCheck { areNotificationsAllowed(context) }
    val channel by rememberCheck { isProgressChannelUsable(context) }
    val battery by rememberCheck { isIgnoringBatteryOptimizations(context) }
    val vendor = remember { aggressiveBatteryVendor() }

    Text(
        text = stringResource(R.string.settings_reliability),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    CheckNotice(notifications, R.string.check_notifications_off, R.string.check_open_notification_settings) {
        openAppNotificationSettings(context)
    }
    CheckNotice(channel, R.string.check_channel_off, R.string.check_open_notification_settings) {
        openAppNotificationSettings(context)
    }
    CheckNotice(battery, R.string.settings_battery_off, R.string.battery_hint_allow) {
        requestIgnoreBatteryOptimizations(context)
    }
    if (vendor != null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.check_vendor_title, vendor), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.check_vendor_steps), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { openAppDetails(context) }) { Text(stringResource(R.string.check_open_app_settings)) }
        }
    }
    if (notifications && channel && battery && vendor == null) {
        Text(
            text = stringResource(R.string.check_all_ok),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
