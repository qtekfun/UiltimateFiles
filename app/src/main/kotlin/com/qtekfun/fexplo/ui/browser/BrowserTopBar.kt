package com.qtekfun.fexplo.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.BreadcrumbSegment
import com.qtekfun.fexplo.core.model.SortField
import com.qtekfun.fexplo.core.model.SortOrder
import com.qtekfun.fexplo.core.model.ViewMode
import com.qtekfun.fexplo.ui.components.BreadcrumbBar
import com.qtekfun.fexplo.ui.components.labelRes

/** Top bar at rest: drawer, interactive breadcrumb, search and the directory overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTopBar(
    segments: List<BreadcrumbSegment>,
    sortOrder: SortOrder,
    viewMode: ViewMode,
    searchQuery: String?,
    onMenuClick: () -> Unit,
    onSegmentClick: (BreadcrumbSegment) -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onSortSelected: (SortField) -> Unit,
    onSelectAll: () -> Unit,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        modifier = modifier,
        windowInsets = windowInsets,
        title = {
            if (searchQuery == null) {
                BreadcrumbBar(segments = segments, onSegmentClick = onSegmentClick)
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        navigationIcon = {
            if (searchQuery == null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_open_drawer))
                }
            } else {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_search))
                }
            }
        },
        actions = {
            if (searchQuery == null) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        onClick = { menuOpen = false; onNewFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_file)) },
                        onClick = { menuOpen = false; onNewFile() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_sort_by)) },
                        enabled = false,
                        onClick = {},
                    )
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(stringResource(field.labelRes())) },
                            trailingIcon = {
                                if (field == sortOrder.field) {
                                    Icon(
                                        imageVector = if (sortOrder.ascending) {
                                            Icons.Filled.ArrowUpward
                                        } else {
                                            Icons.Filled.ArrowDownward
                                        },
                                        contentDescription = null,
                                    )
                                }
                            },
                            onClick = { menuOpen = false; onSortSelected(field) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (viewMode == ViewMode.LIST) R.string.action_view_grid else R.string.action_view_list))
                        },
                        onClick = { menuOpen = false; onToggleViewMode() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        onClick = { menuOpen = false; onSelectAll() },
                    )
                }
            }
        },
    )
}
