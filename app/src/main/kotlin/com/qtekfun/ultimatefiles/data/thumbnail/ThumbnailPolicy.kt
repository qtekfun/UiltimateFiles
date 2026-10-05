package com.qtekfun.ultimatefiles.data.thumbnail

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.util.FileKind
import com.qtekfun.ultimatefiles.core.util.kind
import com.qtekfun.ultimatefiles.data.repository.SftpFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.SmbFileSystemRepository
import com.qtekfun.ultimatefiles.data.repository.WebDavFileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.ArchivePaths
import kotlin.math.min

/** Which files get a thumbnail and how big a decoded picture has to be; pure so it can be unit tested. */
object ThumbnailPolicy {
    /** Photos on network accounts bigger than this are never fetched just to draw a thumbnail. */
    const val NETWORK_IMAGE_MAX_BYTES = 8L * 1024 * 1024

    enum class Source { LOCAL, SAF, NETWORK, ARCHIVE }

    /** Mirrors the routing of `RoutingFileSystemRepository`. */
    fun sourceOf(path: String): Source = when {
        path.startsWith("content://") -> Source.SAF
        path.startsWith(WebDavFileSystemRepository.SCHEME) ||
            path.startsWith(SftpFileSystemRepository.SCHEME) ||
            path.startsWith(SmbFileSystemRepository.SCHEME) -> Source.NETWORK
        ArchivePaths.isArchivePath(path) -> Source.ARCHIVE
        else -> Source.LOCAL
    }

    /** Whether the row/cell should be laid out as a picture: a photo or video that is not inside an archive. */
    fun mayHaveThumbnail(item: FileItem): Boolean =
        !item.isDirectory && item.kind().let { it == FileKind.IMAGE || it == FileKind.VIDEO } && sourceOf(item.path) != Source.ARCHIVE

    /** Whether a thumbnail may actually be produced now; network accounts only when the user allowed it, for small photos. */
    fun canLoad(item: FileItem, allowNetwork: Boolean): Boolean {
        if (!mayHaveThumbnail(item)) return false
        if (sourceOf(item.path) != Source.NETWORK) return true
        return allowNetwork && item.kind() == FileKind.IMAGE && item.sizeBytes in 1..NETWORK_IMAGE_MAX_BYTES
    }

    /**
     * Largest power-of-two `inSampleSize` that keeps the shorter side of the picture at least [targetPx] long,
     * so a centre-cropped thumbnail is never upscaled.
     */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        val shorter = min(width, height)
        var sample = 1
        while (shorter / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** Size that makes the shorter side [targetPx] keeping the aspect ratio, or null when the picture is not bigger than that. */
    fun scaledSize(width: Int, height: Int, targetPx: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || targetPx <= 0 || min(width, height) <= targetPx) return null
        val scale = targetPx.toDouble() / min(width, height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}
