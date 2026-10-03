package com.qtekfun.ultimatefiles.core.model

/** [COMPRESS] and [EXTRACT] run through the same queue as copies but never appear on the clipboard. */
enum class OperationType { COPY, CUT, COMPRESS, EXTRACT }

/** Items waiting to be pasted; shared between both panels. */
data class ClipboardState(
    val operation: OperationType,
    val sourcePath: String,
    val items: List<FileItem>,
)
