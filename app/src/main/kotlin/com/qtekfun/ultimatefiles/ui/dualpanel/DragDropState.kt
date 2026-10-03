package com.qtekfun.ultimatefiles.ui.dualpanel

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.PanelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Items being dragged out of a panel. */
data class DragPayload(val items: List<FileItem>, val sourcePanel: PanelId, val sourcePath: String)

/** A completed drop waiting for the user to choose copy or move. */
data class PendingDrop(val items: List<FileItem>, val targetPath: String)

/**
 * In-app drag and drop between the two panels. The system drag carries only a marker; the real
 * items travel through this holder so no file path is ever serialised into a `ClipData`.
 */
class DragDropState {
    private val _payload = MutableStateFlow<DragPayload?>(null)
    val payload: StateFlow<DragPayload?> = _payload.asStateFlow()

    private val _pendingDrop = MutableStateFlow<PendingDrop?>(null)
    val pendingDrop: StateFlow<PendingDrop?> = _pendingDrop.asStateFlow()

    fun start(payload: DragPayload) {
        _payload.value = payload
    }

    /** Returns true when the drop was accepted (and now awaits the copy/move choice). */
    fun requestDrop(targetPath: String): Boolean {
        val dragged = _payload.value ?: return false
        _payload.value = null
        val ignorable = targetPath == dragged.sourcePath || dragged.items.any { it.path == targetPath }
        if (ignorable) return false
        _pendingDrop.value = PendingDrop(dragged.items, targetPath)
        return true
    }

    fun cancelDrag() {
        _payload.value = null
    }

    fun clearPendingDrop() {
        _pendingDrop.value = null
    }

    companion object {
        /** Label of the `ClipData` that marks a drag as ours. */
        const val CLIP_LABEL = "ultimatefiles-items"
    }
}
