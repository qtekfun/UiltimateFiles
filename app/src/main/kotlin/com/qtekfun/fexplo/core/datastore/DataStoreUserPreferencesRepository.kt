package com.qtekfun.fexplo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qtekfun.fexplo.core.model.PanelId
import com.qtekfun.fexplo.core.model.SortField
import com.qtekfun.fexplo.core.model.SortOrder
import com.qtekfun.fexplo.core.model.ThemeMode
import com.qtekfun.fexplo.core.model.UserPreferences
import com.qtekfun.fexplo.core.model.ViewMode
import com.qtekfun.fexplo.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreUserPreferencesRepository(private val store: DataStore<Preferences>) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = store.data.map { prefs ->
        UserPreferences(
            viewMode = prefs[VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST,
            sortOrder = SortOrder(
                field = prefs[SORT_FIELD]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.NAME,
                ascending = prefs[SORT_ASCENDING] ?: true,
            ),
            lastDirectoryPaths = PanelId.entries.associateWith { prefs[lastPathKey(it)] },
            themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
        )
    }

    override suspend fun setViewMode(mode: ViewMode) {
        store.edit { it[VIEW_MODE] = mode.name }
    }

    override suspend fun setSortOrder(order: SortOrder) {
        store.edit {
            it[SORT_FIELD] = order.field.name
            it[SORT_ASCENDING] = order.ascending
        }
    }

    override suspend fun setLastDirectory(panel: PanelId, directoryPath: String) {
        store.edit { it[lastPathKey(panel)] = directoryPath }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        fun lastPathKey(panel: PanelId) = stringPreferencesKey("last_path_${panel.name.lowercase()}")
    }
}
