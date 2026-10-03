package com.qtekfun.ultimatefiles.core.model

enum class PanelId { LEFT, RIGHT }

enum class ViewMode { LIST, GRID }

/** [AMOLED] is the dark theme with pure black backgrounds. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class SortField { NAME, SIZE, DATE }

data class SortOrder(val field: SortField = SortField.NAME, val ascending: Boolean = true)

data class UserPreferences(
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOrder: SortOrder = SortOrder(),
    /** Last opened directory path per panel; null means "use the default root". */
    val lastDirectoryPaths: Map<PanelId, String?> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colours from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Read back and checksum every copied file (slower, safer for big or irreplaceable files). */
    val verifyCopies: Boolean = false,
)
