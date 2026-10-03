package com.qtekfun.ultimatefiles.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qtekfun.ultimatefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Ready<T>(val value: T) : Loaded<T>
    data class Failed(val message: String?) : Loaded<Nothing>
}

private const val MAX_IMAGE_SIDE = 2048
private const val MAX_TEXT_BYTES = 1024 * 1024

@Composable
internal fun ImageViewer(uri: Uri, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val state by produceState<Loaded<Bitmap>>(Loaded.Loading, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                bitmap ?: error("Cannot decode image")
            }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.message) })
        }
    }
    when (val current = state) {
        Loaded.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator() }
        is Loaded.Failed -> ViewerMessage(R.string.viewer_error, modifier, current.message)
        is Loaded.Ready -> {
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            Image(
                bitmap = current.value.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
        }
    }
}

@Composable
internal fun TextViewer(uri: Uri, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val state by produceState<Loaded<Pair<String, Boolean>>>(Loaded.Loading, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)!!.use { input ->
                    val buffer = ByteArray(MAX_TEXT_BYTES + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    val truncated = read > MAX_TEXT_BYTES
                    String(buffer, 0, minOf(read, MAX_TEXT_BYTES), Charsets.UTF_8) to truncated
                }
            }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.message) })
        }
    }
    when (val current = state) {
        Loaded.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator() }
        is Loaded.Failed -> ViewerMessage(R.string.viewer_error, modifier, current.message)
        is Loaded.Ready -> {
            val (text, truncated) = current.value
            SelectionContainer {
                androidx.compose.foundation.layout.Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (truncated) {
                        Text(stringResource(R.string.viewer_truncated), color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** PDF pages are rendered one at a time on demand: [PdfRenderer] allows a single open page. */
@Composable
internal fun PdfViewer(uri: Uri, modifier: Modifier) {
    val resolver = LocalContext.current.contentResolver
    val lock = remember { Mutex() }
    val renderer by produceState<Loaded<PdfRenderer>>(Loaded.Loading, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = resolver.openFileDescriptor(uri, "r") ?: error("Cannot open the file")
                PdfRenderer(descriptor)
            }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.message) })
        }
    }
    DisposableEffect(uri) {
        onDispose { (renderer as? Loaded.Ready)?.value?.close() }
    }
    when (val current = renderer) {
        Loaded.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator() }
        is Loaded.Failed -> ViewerMessage(R.string.viewer_error, modifier, current.message)
        is Loaded.Ready -> {
            val pdf = current.value
            LazyColumn(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                itemsIndexed(List(pdf.pageCount) { it }) { _, index ->
                    PdfPage(pdf, index, lock)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfRenderer, index: Int, lock: Mutex) {
    val widthPx = LocalContext.current.resources.displayMetrics.widthPixels
    val page by produceState<Bitmap?>(null, index) {
        value = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    pdf.openPage(index).use { page ->
                        val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val bitmap = page
    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) { CircularProgressIndicator() }
    } else {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

@Composable
internal fun MediaViewer(uri: Uri, modifier: Modifier) {
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        ViewerMessage(R.string.viewer_error, modifier)
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(uri)
                setOnErrorListener { _, _, _ ->
                    failed = true
                    true
                }
                setOnPreparedListener { start() }
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
