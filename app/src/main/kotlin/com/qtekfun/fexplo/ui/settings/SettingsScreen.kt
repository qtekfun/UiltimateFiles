package com.qtekfun.fexplo.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.ThemeMode
import com.qtekfun.fexplo.core.model.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
