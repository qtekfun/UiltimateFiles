package com.qtekfun.ultimatefiles.core.model

enum class OperationType { COPY, CUT }

/** Items waiting to be pasted; shared between both panels. */
data class ClipboardState(
    val operation: OperationType,
    val sourcePath: String,
    val items: List<FileItem>,
)
