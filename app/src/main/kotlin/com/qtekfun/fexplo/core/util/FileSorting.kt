package com.qtekfun.fexplo.core.util

import com.qtekfun.fexplo.core.model.FileItem
import com.qtekfun.fexplo.core.model.SortField
import com.qtekfun.fexplo.core.model.SortOrder

/** Directories always come first; [order] applies within each group. */
fun List<FileItem>.sortedByOrder(order: SortOrder): List<FileItem> {
    val byField: Comparator<FileItem> = when (order.field) {
        SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortField.SIZE -> compareBy { it.sizeBytes }
        SortField.DATE -> compareBy { it.lastModifiedMillis }
    }
    val directed = if (order.ascending) byField else byField.reversed()
    return sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(directed))
}
