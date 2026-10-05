package com.qtekfun.ultimatefiles.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.core.model.UserPreferences
import com.qtekfun.ultimatefiles.core.model.ViewMode
import com.qtekfun.ultimatefiles.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreUserPreferencesRepository(private val store: DataStore<Preferences>) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = store.data.map { prefs ->
        val panelIds = readPanelIds(prefs)
        UserPreferences(
            viewMode = prefs[VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST,
            sortOrder = SortOrder(
                field = prefs[SORT_FIELD]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.NAME,
                ascending = prefs[SORT_ASCENDING] ?: true,
            ),
            panelIds = panelIds,
            lastDirectoryPaths = panelIds.associateWith { prefs[lastPathKey(it)] },
            panelBarPosition = prefs[PANEL_BAR_POSITION]?.let { runCatching { PanelBarPosition.valueOf(it) }.getOrNull() } ?: PanelBarPosition.TOP,
            themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            verifyCopies = prefs[VERIFY_COPIES] ?: false,
            thumbnailsOnNetwork = prefs[THUMBNAILS_ON_NETWORK] ?: false,
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

    override suspend fun setPanelIds(panels: List<PanelId>) {
        store.edit { prefs ->
            (readPanelIds(prefs) - panels.toSet()).forEach { prefs.remove(lastPathKey(it)) }
            prefs[PANEL_IDS] = panels.joinToString(",") { it.value.toString() }
        }
    }

    override suspend fun setPanelBarPosition(position: PanelBarPosition) {
        store.edit { it[PANEL_BAR_POSITION] = position.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setVerifyCopies(enabled: Boolean) {
        store.edit { it[VERIFY_COPIES] = enabled }
    }

    override suspend fun setThumbnailsOnNetwork(enabled: Boolean) {
        store.edit { it[THUMBNAILS_ON_NETWORK] = enabled }
    }

    private companion object {
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VERIFY_COPIES = booleanPreferencesKey("verify_copies")
        val THUMBNAILS_ON_NETWORK = booleanPreferencesKey("thumbnails_on_network")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val PANEL_IDS = stringPreferencesKey("panel_ids")
        val PANEL_BAR_POSITION = stringPreferencesKey("panel_bar_position")

        /** The first two panels keep the keys of the original left/right panels. */
        fun lastPathKey(panel: PanelId) = stringPreferencesKey(
            when (panel) {
                PanelId.LEFT -> "last_path_left"
                PanelId.RIGHT -> "last_path_right"
                else -> "last_path_${panel.value}"
            },
        )

        fun readPanelIds(prefs: Preferences): List<PanelId> {
            val ids = prefs[PANEL_IDS]?.split(',')?.mapNotNull { it.toIntOrNull()?.let(::PanelId) }?.distinct().orEmpty()
            return if (ids.size >= 2) ids else PanelId.DEFAULTS
        }
    }
}
