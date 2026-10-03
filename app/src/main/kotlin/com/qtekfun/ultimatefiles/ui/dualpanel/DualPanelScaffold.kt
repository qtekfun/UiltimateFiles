package com.qtekfun.ultimatefiles.ui.dualpanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.ui.browser.BrowserScreen
import com.qtekfun.ultimatefiles.ui.browser.BrowserViewModel
import kotlinx.coroutines.launch

/** Width (dp) from which both panels are shown side by side; mirrors the Medium window size class. */
const val SPLIT_MIN_WIDTH_DP = 600

fun isSplitLayout(widthDp: Int): Boolean = widthDp >= SPLIT_MIN_WIDTH_DP

/**
 * Compact widths (portrait phones): a pager with one panel per tab. Wider windows (landscape,
 * tablets): a fixed 50/50 split with both panels visible, which is also where drag and drop works.
 */
@Composable
fun DualPanelScaffold(
    left: BrowserViewModel,
    right: BrowserViewModel,
    activePanel: PanelId,
    onActivePanelChange: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isSplitLayout(LocalConfiguration.current.screenWidthDp)) {
        SplitPanels(left, right, activePanel, onActivePanelChange, onOpenDrawer, modifier)
    } else {
        PagedPanels(left, right, onActivePanelChange, onOpenDrawer, modifier)
    }
}

@Composable
private fun SplitPanels(
    left: BrowserViewModel,
    right: BrowserViewModel,
    activePanel: PanelId,
    onActivePanelChange: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        PanelId.entries.forEach { panel ->
            if (panel == PanelId.RIGHT) VerticalDivider()
            BrowserScreen(
                viewModel = if (panel == PanelId.LEFT) left else right,
                isActive = activePanel == panel,
                dragAndDropEnabled = true,
                onOpenDrawer = onOpenDrawer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .activateOnTouch(panel, onActivePanelChange),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PagedPanels(
    left: BrowserViewModel,
    right: BrowserViewModel,
    onActivePanelChange: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState { PanelId.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { onActivePanelChange(PanelId.entries[pagerState.currentPage]) }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier.statusBarsPadding()) {
                PanelId.entries.forEachIndexed { index, _ ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(R.string.panel_tab_format, index + 1)) },
                    )
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            BrowserScreen(
                viewModel = if (page == 0) left else right,
                isActive = pagerState.currentPage == page,
                dragAndDropEnabled = false,
                onOpenDrawer = onOpenDrawer,
                // The tab row above already consumes the status bar inset.
                topInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }
}

/** Marks [panel] as the one the drawer and back button act on as soon as it is touched. */
private fun Modifier.activateOnTouch(panel: PanelId, onActivate: (PanelId) -> Unit): Modifier =
    pointerInput(panel) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) onActivate(panel)
            }
        }
    }
