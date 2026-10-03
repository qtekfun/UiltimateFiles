package com.qtekfun.ultimatefiles.core.util

import com.qtekfun.ultimatefiles.core.model.FileItem

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, PDF, TEXT, ARCHIVE, APK, OTHER }

private val ARCHIVE_TYPES = setOf(
    "application/zip",
    "application/x-tar",
    "application/gzip",
    "application/x-gzip",
    "application/x-7z-compressed",
    "application/vnd.rar",
    "application/x-rar-compressed",
)

/** Coarse classification used to pick the row icon; based on the MIME type, falling back to the extension. */
fun FileItem.kind(): FileKind {
    if (isDirectory) return FileKind.FOLDER
    val type = mimeType ?: MimeTypes.fromName(name)
    return when {
        type == null -> FileKind.OTHER
        type.startsWith("image/") -> FileKind.IMAGE
        type.startsWith("video/") -> FileKind.VIDEO
        type.startsWith("audio/") -> FileKind.AUDIO
        type == "application/pdf" -> FileKind.PDF
        type == "application/vnd.android.package-archive" -> FileKind.APK
        type in ARCHIVE_TYPES -> FileKind.ARCHIVE
        type.startsWith("text/") -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}
