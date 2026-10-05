package com.qtekfun.ultimatefiles.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.util.FileKind
import com.qtekfun.ultimatefiles.core.util.SizedLruCache
import com.qtekfun.ultimatefiles.core.util.kind
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Draws the pictures shown in place of the icon of photos and videos. Photos are decoded through
 * [FileSystemRepository] (so every backend works), at a reduced size; videos use one of their frames,
 * which Android can only read from a local path or a `content://` URI, so they are limited to those.
 *
 * Results are kept in memory (bounded by bytes), failures are remembered so a broken file is not retried on
 * every scroll, and only a few pictures are decoded at once.
 */
class ThumbnailLoader(
    private val context: Context,
    private val repository: FileSystemRepository,
    private val preferences: UserPreferencesRepository,
) {
    private val cache = SizedLruCache<String, Bitmap>(Runtime.getRuntime().maxMemory() / 16) { it.byteCount.toLong() }
    private val failures = SizedLruCache<String, Boolean>(MAX_REMEMBERED_FAILURES.toLong()) { 1 }
    private val permits = Semaphore(MAX_PARALLEL)

    /** The thumbnail if it is already in memory; lets the UI show it on the first frame when scrolling back. */
    fun cached(item: FileItem, sizePx: Int): Bitmap? = cache[keyOf(item, sizePx)]

    /** Loads (or fetches from memory) a thumbnail whose shorter side is about [sizePx]; null when there is none. */
    suspend fun load(item: FileItem, sizePx: Int): Bitmap? {
        val key = keyOf(item, sizePx)
        cache[key]?.let { return it }
        if (failures[key] != null) return null
        if (!ThumbnailPolicy.canLoad(item, preferences.preferences.first().thumbnailsOnNetwork)) return null
        return permits.withPermit {
            cache[key]?.let { return@withPermit it }
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (item.kind() == FileKind.VIDEO) videoFrame(item, sizePx) else photo(item, sizePx)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    null
                } catch (e: RuntimeException) {
                    // Corrupt pictures and codecs that refuse a file throw all sorts of runtime exceptions.
                    null
                }
            }
            if (bitmap != null) cache[key] = bitmap else failures[key] = true
            bitmap
        }
    }

    private fun keyOf(item: FileItem, sizePx: Int) = "${item.path}|${item.lastModifiedMillis}|${item.sizeBytes}|$sizePx"

    private suspend fun photo(item: FileItem, sizePx: Int): Bitmap? {
        val small = item.sizeBytes in 1..IN_MEMORY_LIMIT
        if (small) {
            val bytes = repository.openInput(item).getOrNull()?.use { it.readBytes() } ?: return null
            currentCoroutineContext().ensureActive()
            return decode(item, sizePx) { ByteArrayInputStream(bytes) }
        }
        // Big or unknown size: stream it again for every pass instead of holding it in memory (local and SAF only).
        return decode(item, sizePx) { repository.openInput(item).getOrNull() }
    }

    private suspend fun decode(item: FileItem, sizePx: Int, open: suspend () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the call fills in the sizes and always returns null, so the result says nothing.
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        currentCoroutineContext().ensureActive()
        val options = BitmapFactory.Options().apply {
            inSampleSize = ThumbnailPolicy.sampleSize(bounds.outWidth, bounds.outHeight, sizePx)
        }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        currentCoroutineContext().ensureActive()
        val degrees = open()?.use { orientationOf(it) } ?: 0
        return if (degrees == 0) decoded else rotated(decoded, degrees)
    }

    private fun orientationOf(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0 // PNG, WebP and others without EXIF data
    }

    private fun rotated(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun videoFrame(item: FileItem, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            when (ThumbnailPolicy.sourceOf(item.path)) {
                ThumbnailPolicy.Source.LOCAL -> retriever.setDataSource(item.path)
                ThumbnailPolicy.Source.SAF -> retriever.setDataSource(context, Uri.parse(item.path))
                else -> return null
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // Not the very first frame, which is often black.
            val atUs = (durationMs / 10).coerceAtMost(MAX_FRAME_OFFSET_MS) * 1000
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (shownWidth, shownHeight) = if (rotation == 90 || rotation == 270) height to width else width to height
            val target = ThumbnailPolicy.scaledSize(shownWidth, shownHeight, sizePx)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && target != null) {
                retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, target.first, target.second)
            } else {
                retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val MAX_REMEMBERED_FAILURES = 2000
        const val IN_MEMORY_LIMIT = 8L * 1024 * 1024
        const val MAX_FRAME_OFFSET_MS = 5_000L
    }
}
