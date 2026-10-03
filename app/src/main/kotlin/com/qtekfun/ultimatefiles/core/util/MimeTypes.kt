package com.qtekfun.ultimatefiles.core.util

import android.webkit.MimeTypeMap

object MimeTypes {
    const val OCTET_STREAM = "application/octet-stream"

    /** Best-effort MIME type from the file extension, or null when unknown. */
    fun fromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return null
        return MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension)
    }
}
