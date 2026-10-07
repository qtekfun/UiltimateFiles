package com.qtekfun.ultimatefiles.ui.browser

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.util.FileKind
import com.qtekfun.ultimatefiles.core.util.kind
import com.qtekfun.ultimatefiles.data.thumbnail.ThumbnailLoader
import com.qtekfun.ultimatefiles.data.thumbnail.ThumbnailPolicy

/** Where thumbnails come from; null (previews, tests) simply keeps the icons. */
val LocalThumbnailLoader = staticCompositionLocalOf<ThumbnailLoader?> { null }

private val ListThumbnailSize = 56.dp
private val GridThumbnailSize = 120.dp
private val ThumbnailShape = RoundedCornerShape(8.dp)

/**
 * What a list row shows at its start: the file's thumbnail if it is a photo or video, otherwise its icon.
 * [modifier] carries the gestures (tap to select, drag source); the size is set here.
 */
@Composable
fun FileLeading(item: FileItem, selected: Boolean, modifier: Modifier = Modifier) {
    if (ThumbnailPolicy.mayHaveThumbnail(item)) {
        MediaThumbnail(item, selected, ListThumbnailSize, modifier.size(ListThumbnailSize))
    } else {
        IconTile(item, selected, modifier.size(ListThumbnailSize), tileSize = 48.dp, iconSize = 24.dp)
    }
}

/** The grid counterpart: a thumbnail filling the cell's width for photos and videos, the icon otherwise. */
@Composable
fun FileTile(item: FileItem, selected: Boolean, modifier: Modifier = Modifier) {
    if (ThumbnailPolicy.mayHaveThumbnail(item)) {
        MediaThumbnail(item, selected, GridThumbnailSize, modifier.fillMaxWidth().aspectRatio(1f))
    } else {
        IconTile(item, selected, modifier.size(56.dp), tileSize = 56.dp, iconSize = 28.dp)
    }
}

/** Container and content colours of a kind's icon tile, taken from the colour scheme so dynamic colour carries through. */
@Composable
private fun FileKind.tone(): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        FileKind.FOLDER -> colors.primaryContainer to colors.onPrimaryContainer
        FileKind.IMAGE, FileKind.VIDEO -> colors.tertiaryContainer to colors.onTertiaryContainer
        FileKind.AUDIO, FileKind.ARCHIVE, FileKind.APK -> colors.secondaryContainer to colors.onSecondaryContainer
        FileKind.PDF -> colors.errorContainer to colors.onErrorContainer
        FileKind.TEXT, FileKind.OTHER -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
}

/** The icon of a file kind on a rounded tonal tile; [modifier] sets the cell, [tileSize] the tile inside it. */
@Composable
private fun IconTile(item: FileItem, selected: Boolean, modifier: Modifier, tileSize: Dp, iconSize: Dp) {
    val (kindContainer, kindContent) = item.kind().tone()
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else kindContainer, label = "tile")
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else kindContent, label = "tileIcon")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(tileSize).clip(RoundedCornerShape(14.dp)).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.Check else item.kind().icon(),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = content,
            )
        }
    }
}

@Composable
private fun MediaThumbnail(item: FileItem, selected: Boolean, requestSize: Dp, modifier: Modifier) {
    val thumbnail by rememberThumbnail(item, requestSize)
    Box(
        modifier = modifier.clip(ThumbnailShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = thumbnail, label = "thumbnail") { bitmap ->
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(item.kind().icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.kind() == FileKind.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.thumbnail_video),
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Loads off the main thread; leaving the screen (scrolling away) cancels the work. */
@Composable
private fun rememberThumbnail(item: FileItem, size: Dp): State<ImageBitmap?> {
    val loader = LocalThumbnailLoader.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return produceState(
        initialValue = loader?.cached(item, sizePx)?.asImageBitmap(),
        item.path, item.lastModifiedMillis, item.sizeBytes, sizePx, loader,
    ) {
        value = loader?.load(item, sizePx)?.asImageBitmap()
    }
}
