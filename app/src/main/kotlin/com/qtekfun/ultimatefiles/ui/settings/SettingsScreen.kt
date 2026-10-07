package com.qtekfun.ultimatefiles.ui.settings

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.annotation.StringRes
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
import com.qtekfun.ultimatefiles.BuildConfig
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
    versionName: String = BuildConfig.VERSION_NAME,
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppHeader(versionName)
            SettingsGroup(R.string.settings_theme, Icons.Filled.Palette) {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        val description = mode.descriptionRes()
                        ListItem(
                            modifier = Modifier.selectable(
                                selected = preferences.themeMode == mode,
                                role = Role.RadioButton,
                                onClick = { onThemeMode(mode) },
                            ),
                            colors = rowColors(),
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                        supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                        trailingContent = { Switch(checked = preferences.dynamicColor, onCheckedChange = null) },
                        colors = rowColors(),
                        modifier = Modifier.selectable(
                            selected = preferences.dynamicColor,
                            role = Role.Switch,
                            onClick = { onDynamicColor(!preferences.dynamicColor) },
                        ),
                    )
                }
            }
            SettingsGroup(R.string.settings_panels, Icons.Filled.ViewColumn) {
                Column(Modifier.selectableGroup()) {
                    PanelBarPosition.entries.forEach { position ->
                        ListItem(
                            modifier = Modifier.selectable(
                                selected = preferences.panelBarPosition == position,
                                role = Role.RadioButton,
                                onClick = { onPanelBarPosition(position) },
                            ),
                            colors = rowColors(),
                            headlineContent = { Text(stringResource(position.labelRes())) },
                            leadingContent = { RadioButton(selected = preferences.panelBarPosition == position, onClick = null) },
                        )
                    }
                }
            }
            SettingsGroup(R.string.settings_thumbnails, Icons.Filled.Image) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_thumbnails_network)) },
                    supportingContent = { Text(stringResource(R.string.settings_thumbnails_network_summary)) },
                    trailingContent = { Switch(checked = preferences.thumbnailsOnNetwork, onCheckedChange = null) },
                    colors = rowColors(),
                    modifier = Modifier.selectable(
                        selected = preferences.thumbnailsOnNetwork,
                        role = Role.Switch,
                        onClick = { onThumbnailsOnNetwork(!preferences.thumbnailsOnNetwork) },
                    ),
                )
            }
            SettingsGroup(R.string.settings_transfers, Icons.Filled.SwapHoriz) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_verify_copies)) },
                    supportingContent = { Text(stringResource(R.string.settings_verify_copies_summary)) },
                    trailingContent = { Switch(checked = preferences.verifyCopies, onCheckedChange = null) },
                    colors = rowColors(),
                    modifier = Modifier.selectable(
                        selected = preferences.verifyCopies,
                        role = Role.Switch,
                        onClick = { onVerifyCopies(!preferences.verifyCopies) },
                    ),
                )
            }
            ReliabilitySection()
            SettingsGroup(R.string.settings_backup, Icons.Filled.Backup) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                    leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    colors = rowColors(),
                    modifier = Modifier.clickable { showExport = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                    colors = rowColors(),
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
                )
            }
            SettingsGroup(R.string.settings_about, Icons.Filled.Info) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version, versionName)) },
                    leadingContent = { Icon(Icons.Filled.Tag, contentDescription = null) },
                    colors = rowColors(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_license)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_license_summary)) },
                    leadingContent = { Icon(Icons.Filled.Gavel, contentDescription = null) },
                    colors = rowColors(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_source)) },
                    supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
                    leadingContent = { Icon(Icons.Filled.Code, contentDescription = null) },
                    colors = rowColors(),
                    modifier = Modifier.clickable { uriHandler.openUri(SOURCE_URL) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val SOURCE_URL = "https://github.com/qtekfun/UltimateFiles"

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A titled card holding the rows of one kind of setting. */
@Composable
private fun SettingsGroup(@StringRes title: Int, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** The app's icon, name and version at the top, so the version is the first thing the page says. */
@Composable
private fun AppHeader(versionName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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

    SettingsGroup(R.string.settings_reliability, Icons.Filled.HealthAndSafety) {
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
}
