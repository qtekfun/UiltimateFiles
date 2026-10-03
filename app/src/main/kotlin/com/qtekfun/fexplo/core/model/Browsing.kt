package com.qtekfun.fexplo.core.model

/** One clickable step of the path shown in the breadcrumb bar. */
data class BreadcrumbSegment(val label: String, val path: String)

data class FileHashes(val md5: String, val sha256: String)

/** State of the asynchronous hash calculation shown in the properties sheet. */
sealed interface HashState {
    data object Idle : HashState
    data object Computing : HashState
    data class Done(val hashes: FileHashes) : HashState
    data class Failed(val message: String?) : HashState
}
