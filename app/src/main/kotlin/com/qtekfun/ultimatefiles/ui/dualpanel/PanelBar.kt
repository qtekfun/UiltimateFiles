package com.qtekfun.ultimatefiles.ui.dualpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId

/** Name shown for a panel: the folder it is in, or "Panel N" while it has not loaded one yet. */
@Composable
private fun PanelEntry.folderName(index: Int): String {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state.breadcrumb.lastOrNull()?.label ?: stringResource(R.string.panel_tab_format, index + 1)
}

/**
 * The bar that says which panel a slot shows (by its folder) and lets you pick another one from a
 * drop-down of all open panels, add a panel, or close this one. [isActive] puts an accent line on the
 * edge facing the panel and dims the bar otherwise.
 */
@Composable
fun PanelBar(
    panels: List<PanelEntry>,
    shownIndex: Int,
    isActive: Boolean,
    position: PanelBarPosition,
    /** The panel the other slot shows (landscape), flagged in the list because choosing it swaps the two. */
    otherShown: PanelId?,
    onSelect: (PanelId) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atTop = position == PanelBarPosition.TOP
    var expanded by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .then(if (atTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                    .heightIn(min = 48.dp)
                    .alpha(if (isActive) 1f else 0.65f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .padding(start = 16.dp, end = 8.dp)
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = panels[shownIndex].folderName(shownIndex),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = stringResource(R.string.panel_position_format, shownIndex + 1, panels.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.panel_select))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        panels.forEachIndexed { index, entry ->
                            val state by entry.viewModel.state.collectAsStateWithLifecycle()
                            DropdownMenuItem(
                                text = {
                                    androidx.compose.foundation.layout.Column {
                                        Text(
                                            text = stringResource(R.string.panel_item_format, index + 1, entry.folderName(index)),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        state.currentPath?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    if (index == shownIndex) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (entry.id == otherShown) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.panel_shown_other_side))
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    onSelect(entry.id)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.panel_new)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onAdd()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.panel_close)) },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            enabled = panels.size > MIN_PANELS,
                            onClick = {
                                expanded = false
                                onClose()
                            },
                        )
                    }
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.panel_add))
                }
            }
            if (isActive) {
                Box(
                    Modifier
                        .align(if (atTop) Alignment.BottomCenter else Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

const val MIN_PANELS = 2
