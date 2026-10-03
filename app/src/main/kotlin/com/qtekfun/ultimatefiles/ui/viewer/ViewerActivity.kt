package com.qtekfun.ultimatefiles.ui.viewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.domain.usecase.ViewerKind
import com.qtekfun.ultimatefiles.ui.settings.SettingsViewModel
import com.qtekfun.ultimatefiles.ui.theme.UltimateFilesTheme
import org.koin.mp.KoinPlatform

/**
 * Shows images, text, PDFs and audio/video without leaving the app. It only receives a `content://` URI and reads it
 * through the content resolver, so it works for local files and for storage-access-framework documents alike.
 */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri: Uri? = intent.data
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ViewerKind.valueOf(it) }.getOrNull() }
        setContent {
            val koin = remember { KoinPlatform.getKoin() }
            val settings = viewModel<SettingsViewModel>(
                factory = viewModelFactory { initializer { koin.get<SettingsViewModel>() } },
            )
            val preferences by settings.preferences.collectAsStateWithLifecycle()
            UltimateFilesTheme(
                themeMode = preferences?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = preferences?.dynamicColor ?: true,
            ) {
                ViewerScreen(name = name, uri = uri, kind = kind, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_NAME = "viewer_name"
        const val EXTRA_KIND = "viewer_kind"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun ViewerScreen(name: String, uri: Uri?, kind: ViewerKind?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            uri == null || kind == null -> Text(stringResource(R.string.viewer_unsupported), modifier = content)
            kind == ViewerKind.IMAGE -> ImageViewer(uri, content)
            kind == ViewerKind.TEXT -> TextViewer(uri, content)
            kind == ViewerKind.PDF -> PdfViewer(uri, content)
            else -> MediaViewer(uri, content)
        }
    }
}

@androidx.compose.runtime.Composable
internal fun ViewerMessage(resId: Int, modifier: Modifier, detail: String? = null) {
    Text(
        text = listOfNotNull(stringResource(resId), detail).joinToString(": "),
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}
