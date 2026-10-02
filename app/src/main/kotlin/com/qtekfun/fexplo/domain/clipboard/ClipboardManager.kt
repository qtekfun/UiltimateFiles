package com.qtekfun.fexplo.domain.clipboard

import com.qtekfun.fexplo.core.model.ClipboardState
import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.OperationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide copy/cut buffer shared by both panels; `null` means the buffer is empty. */
class ClipboardManager {
    private val _state = MutableStateFlow<ClipboardState?>(null)
    val state: StateFlow<ClipboardState?> = _state.asStateFlow()

    fun copy(sourcePath: String, items: List<FileItem>) = put(OperationType.COPY, sourcePath, items)

    fun cut(sourcePath: String, items: List<FileItem>) = put(OperationType.CUT, sourcePath, items)

    fun clear() {
        _state.value = null
    }

    private fun put(operation: OperationType, sourcePath: String, items: List<FileItem>) {
        _state.value = if (items.isEmpty()) null else ClipboardState(operation, sourcePath, items)
    }
}
