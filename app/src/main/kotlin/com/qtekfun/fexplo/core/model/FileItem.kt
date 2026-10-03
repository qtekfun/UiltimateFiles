package com.qtekfun.fexplo.core.model

/**
 * A file or directory exposed by a [com.qtekfun.fexplo.domain.repository.FileSystemRepository].
 *
 * [path] is an opaque, backend-specific location (an absolute path for the local backend,
 * a document URI for SAF). Callers must never parse it.
 */
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String?,
    val isWritable: Boolean = true,
    val isHidden: Boolean = false,
    /** POSIX permissions such as `rw-r--r--`, when the backend can tell; only filled by `stat`. */
    val permissions: String? = null,
)
