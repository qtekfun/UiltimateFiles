package com.qtekfun.fexplo.domain.repository

import com.qtekfun.fexplo.core.model.PanelId
import com.qtekfun.fexplo.core.model.SortOrder
import com.qtekfun.fexplo.core.model.ThemeMode
import com.qtekfun.fexplo.core.model.UserPreferences
import com.qtekfun.fexplo.core.model.ViewMode
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setViewMode(mode: ViewMode)

    suspend fun setSortOrder(order: SortOrder)

    suspend fun setLastDirectory(panel: PanelId, directoryPath: String)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)
}
