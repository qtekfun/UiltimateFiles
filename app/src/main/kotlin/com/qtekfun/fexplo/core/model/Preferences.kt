package com.qtekfun.fexplo.core.model

enum class PanelId { LEFT, RIGHT }

enum class ViewMode { LIST, GRID }

enum class SortField { NAME, SIZE, DATE }

data class SortOrder(val field: SortField = SortField.NAME, val ascending: Boolean = true)

data class UserPreferences(
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOrder: SortOrder = SortOrder(),
    /** Last opened directory id per panel; null means "use the default root". */
    val lastDirectoryIds: Map<PanelId, String?> = emptyMap(),
)
