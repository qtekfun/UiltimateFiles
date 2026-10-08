package com.qtekfun.ultimatefiles.ui.browser

import androidx.annotation.StringRes
import com.qtekfun.ultimatefiles.core.model.BreadcrumbSegment
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.HashState
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ViewMode

data class BrowserState(
    val currentPath: String? = null,
    val parentPath: String? = null,
    val breadcrumb: List<BreadcrumbSegment> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sortOrder: SortOrder = SortOrder(),
    val viewMode: ViewMode = ViewMode.LIST,
    val dialog: BrowserDialog? = null,
    /** Null when the search field is closed; otherwise the name filter applied to [items]. */
    val searchQuery: String? = null,
) {
    val visibleItems: List<FileItem>
        get() = searchQuery?.takeIf { it.isNotBlank() }
            ?.let { query -> items.filter { it.name.contains(query.trim(), ignoreCase = true) } } ?: items
    val selectedItems: List<FileItem> get() = items.filter { it.path in selectedPaths }
    val isSelecting: Boolean get() = selectedPaths.isNotEmpty()
}

sealed interface BrowserDialog {
    data object NewFolder : BrowserDialog
    data object NewFile : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
    data class ConfirmDelete(val items: List<FileItem>) : BrowserDialog
    data class Compress(val items: List<FileItem>) : BrowserDialog
    data class Properties(val item: FileItem, val hash: HashState = HashState.Idle) : BrowserDialog
}

/** Everything the screen can ask the view model to do. */
sealed interface BrowserEvent {
    data class Navigate(val path: String) : BrowserEvent
    data object NavigateUp : BrowserEvent
    data object Refresh : BrowserEvent
    data object ToggleSearch : BrowserEvent
    data object ToggleViewMode : BrowserEvent
    data class SetSearchQuery(val query: String) : BrowserEvent
    data class OpenItem(val item: FileItem) : BrowserEvent
    data class OpenWith(val item: FileItem) : BrowserEvent
    data class ToggleSelection(val item: FileItem) : BrowserEvent
    data object SelectAll : BrowserEvent
    data object ClearSelection : BrowserEvent
    data class SortBy(val field: SortField) : BrowserEvent
    data class Copy(val items: List<FileItem>) : BrowserEvent
    data class Cut(val items: List<FileItem>) : BrowserEvent
    data object Paste : BrowserEvent
    data object CancelClipboard : BrowserEvent
    data class Share(val items: List<FileItem>) : BrowserEvent
    data class RequestDelete(val items: List<FileItem>) : BrowserEvent
    data class RequestRename(val item: FileItem) : BrowserEvent
    data object RequestNewFolder : BrowserEvent
    data class RequestCompress(val items: List<FileItem>) : BrowserEvent
    data class Extract(val items: List<FileItem>) : BrowserEvent
    data object RequestNewFile : BrowserEvent
    data class ShowProperties(val item: FileItem) : BrowserEvent
    data class ConfirmName(val name: String) : BrowserEvent
    data object ConfirmDelete : BrowserEvent
    data object ComputeHash : BrowserEvent
    data object DismissDialog : BrowserEvent
    data class StartDrag(val item: FileItem) : BrowserEvent
    data class DropItems(val targetPath: String) : BrowserEvent

    /** Shows [item] in its own folder, selected, so the usual actions apply to it (used by the size analysis). */
    data class Reveal(val item: FileItem) : BrowserEvent
}

/** One-shot results that need an Android context (intents, snackbars). */
sealed interface BrowserEffect {
    data class OpenFile(val item: FileItem, val chooser: Boolean) : BrowserEffect
    data class OpenViewer(val item: FileItem) : BrowserEffect
    data class ShareFiles(val items: List<FileItem>) : BrowserEffect
    data class Message(@StringRes val resId: Int, val detail: String? = null) : BrowserEffect
}
