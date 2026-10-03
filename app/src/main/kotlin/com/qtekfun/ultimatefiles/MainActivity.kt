package com.qtekfun.ultimatefiles

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.ui.main.MainScreen
import com.qtekfun.ultimatefiles.ui.main.StoragePermissionGate
import com.qtekfun.ultimatefiles.ui.settings.SettingsViewModel
import com.qtekfun.ultimatefiles.ui.theme.UltimateFilesTheme
import com.qtekfun.ultimatefiles.ui.theme.resolveDarkTheme
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val koin = remember { KoinPlatform.getKoin() }
            val settings = viewModel<SettingsViewModel>(
                factory = viewModelFactory { initializer { koin.get<SettingsViewModel>() } },
            )
            val preferences by settings.preferences.collectAsStateWithLifecycle()
            val themeMode = preferences?.themeMode ?: ThemeMode.SYSTEM
            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())

            // The chosen theme may differ from the system one, so the bar icons must follow it.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            UltimateFilesTheme(themeMode = themeMode, dynamicColor = preferences?.dynamicColor ?: true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Wait for the stored theme so the app does not flash the default one at start.
                    if (preferences != null) StoragePermissionGate { MainScreen() }
                }
            }
        }
    }
}
