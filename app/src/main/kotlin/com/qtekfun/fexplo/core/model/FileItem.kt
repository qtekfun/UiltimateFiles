package com.qtekfun.fexplo.core.model

/**
 * A file or directory exposed by a [com.qtekfun.fexplo.domain.repository.FileSystemRepository].
 *
 * [id] is an opaque, repository-specific identifier (an absolute path for the local
 * repository, a document URI for SAF). Callers must never parse it.
 */
data class FileItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String?,
)
