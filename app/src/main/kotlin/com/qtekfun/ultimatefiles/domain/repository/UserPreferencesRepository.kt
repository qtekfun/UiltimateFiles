package com.qtekfun.ultimatefiles.domain.repository

import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.core.model.ViewMode
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setViewMode(mode: ViewMode)

    suspend fun setSortOrder(order: SortOrder)

    suspend fun setLastDirectory(panel: PanelId, directoryPath: String)

    /** Stores which panels are open; the saved directories of panels no longer listed are forgotten. */
    suspend fun setPanelIds(panels: List<PanelId>)

    suspend fun setPanelBarPosition(position: PanelBarPosition)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setVerifyCopies(enabled: Boolean)

    suspend fun setThumbnailsOnNetwork(enabled: Boolean)
}
