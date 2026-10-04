package com.qtekfun.ultimatefiles.core.model

/** Identifies one browsing panel; the user can create more than the initial two. */
data class PanelId(val value: Int) {
    companion object {
        val LEFT = PanelId(0)
        val RIGHT = PanelId(1)
        val DEFAULTS = listOf(LEFT, RIGHT)
    }
}

enum class ViewMode { LIST, GRID }

/** Where the bar that picks a panel sits: above or below the panels. */
enum class PanelBarPosition { TOP, BOTTOM }

/** [AMOLED] is the dark theme with pure black backgrounds. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class SortField { NAME, SIZE, DATE }

data class SortOrder(val field: SortField = SortField.NAME, val ascending: Boolean = true)

data class UserPreferences(
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOrder: SortOrder = SortOrder(),
    /** The open panels, in display order; there are always at least two. */
    val panelIds: List<PanelId> = PanelId.DEFAULTS,
    /** Last opened directory path per panel; null means "use the default root". */
    val lastDirectoryPaths: Map<PanelId, String?> = emptyMap(),
    val panelBarPosition: PanelBarPosition = PanelBarPosition.TOP,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colours from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Read back and checksum every copied file (slower, safer for big or irreplaceable files). */
    val verifyCopies: Boolean = false,
)
