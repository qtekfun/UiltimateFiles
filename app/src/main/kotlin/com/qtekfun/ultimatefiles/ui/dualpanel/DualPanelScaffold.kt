package com.qtekfun.ultimatefiles.ui.dualpanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import com.qtekfun.ultimatefiles.core.model.PanelBarPosition
import com.qtekfun.ultimatefiles.core.model.PanelId
import com.qtekfun.ultimatefiles.ui.browser.BrowserScreen
import com.qtekfun.ultimatefiles.ui.browser.BrowserViewModel
import com.qtekfun.ultimatefiles.ui.main.PanelSlot

/** Width (dp) from which two panels are shown side by side; mirrors the Medium window size class. */
const val SPLIT_MIN_WIDTH_DP = 600

fun isSplitLayout(widthDp: Int): Boolean = widthDp >= SPLIT_MIN_WIDTH_DP

/** An open panel together with the state holder behind it. */
data class PanelEntry(val id: PanelId, val viewModel: BrowserViewModel)

/**
 * Compact widths (portrait phones): one panel at a time, swiped through like pages. Wider windows
 * (landscape, tablets): two fixed slots, each with its own [PanelBar] to choose which of the open
 * panels it shows; this is also where drag and drop between panels works.
 */
@Composable
fun DualPanelScaffold(
    panels: List<PanelEntry>,
    startPanel: PanelId,
    endPanel: PanelId,
    activePanel: PanelId,
    barPosition: PanelBarPosition,
    onActivePanelChange: (PanelId) -> Unit,
    onShowPanel: (PanelId, PanelSlot) -> Unit,
    onAddPanel: () -> Unit,
    onClosePanel: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (panels.size < MIN_PANELS) return
    if (isSplitLayout(LocalConfiguration.current.screenWidthDp)) {
        SplitPanels(panels, startPanel, endPanel, activePanel, barPosition, onActivePanelChange, onShowPanel, onAddPanel, onClosePanel, onOpenDrawer, modifier)
    } else {
        PagedPanels(panels, activePanel, barPosition, onActivePanelChange, onAddPanel, onClosePanel, onOpenDrawer, modifier)
    }
}

@Composable
private fun SplitPanels(
    panels: List<PanelEntry>,
    startPanel: PanelId,
    endPanel: PanelId,
    activePanel: PanelId,
    barPosition: PanelBarPosition,
    onActivePanelChange: (PanelId) -> Unit,
    onShowPanel: (PanelId, PanelSlot) -> Unit,
    onAddPanel: () -> Unit,
    onClosePanel: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        listOf(PanelSlot.START to startPanel, PanelSlot.END to endPanel).forEach { (slot, id) ->
            if (slot == PanelSlot.END) VerticalDivider()
            val index = panels.indexOfFirst { it.id == id }
            if (index < 0) return@forEach
            val otherId = if (slot == PanelSlot.START) endPanel else startPanel
            val bar = @Composable {
                PanelBar(
                    panels = panels,
                    shownIndex = index,
                    isActive = activePanel == id,
                    position = barPosition,
                    otherShown = otherId,
                    onSelect = { onShowPanel(it, slot) },
                    onAdd = onAddPanel,
                    onClose = { onClosePanel(id) },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .activateOnTouch(id) { onActivePanelChange(id) },
            ) {
                if (barPosition == PanelBarPosition.TOP) bar()
                // A slot that switches to another panel starts from a fresh composition (scroll position, dialogs).
                key(id) {
                    BrowserScreen(
                        viewModel = panels[index].viewModel,
                        isActive = activePanel == id,
                        dragAndDropEnabled = true,
                        onOpenDrawer = onOpenDrawer,
                        modifier = Modifier.weight(1f),
                        topInsets = if (barPosition == PanelBarPosition.TOP) NoInsets else androidx.compose.material3.TopAppBarDefaults.windowInsets,
                        consumeNavigationBar = barPosition == PanelBarPosition.BOTTOM,
                    )
                }
                if (barPosition == PanelBarPosition.BOTTOM) bar()
            }
        }
    }
}

/** The bar above already consumes the status bar inset. */
private val NoInsets = WindowInsets(0, 0, 0, 0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedPanels(
    panels: List<PanelEntry>,
    activePanel: PanelId,
    barPosition: PanelBarPosition,
    onActivePanelChange: (PanelId) -> Unit,
    onAddPanel: () -> Unit,
    onClosePanel: (PanelId) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier,
) {
    val activeIndex = panels.indexOfFirst { it.id == activePanel }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = activeIndex) { panels.size }
    // The bar, adding and closing move the active panel; the pager follows it, and the other way round.
    LaunchedEffect(activeIndex, panels.size) {
        if (pagerState.currentPage != activeIndex) pagerState.animateScrollToPage(activeIndex)
    }
    LaunchedEffect(pagerState.settledPage) {
        panels.getOrNull(pagerState.settledPage)?.let { onActivePanelChange(it.id) }
    }
    val bar = @Composable {
        PanelBar(
            panels = panels,
            shownIndex = activeIndex,
            isActive = true,
            position = barPosition,
            otherShown = null,
            onSelect = onActivePanelChange,
            onAdd = onAddPanel,
            onClose = { onClosePanel(panels[activeIndex].id) },
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (barPosition == PanelBarPosition.TOP) bar()
        HorizontalPager(state = pagerState, key = { panels[it].id.value }, modifier = Modifier.weight(1f)) { page ->
            BrowserScreen(
                viewModel = panels[page].viewModel,
                isActive = pagerState.currentPage == page,
                dragAndDropEnabled = false,
                onOpenDrawer = onOpenDrawer,
                topInsets = if (barPosition == PanelBarPosition.TOP) NoInsets else androidx.compose.material3.TopAppBarDefaults.windowInsets,
                consumeNavigationBar = barPosition == PanelBarPosition.BOTTOM,
            )
        }
        if (barPosition == PanelBarPosition.BOTTOM) bar()
    }
}

/**
 * Marks a panel as the one the drawer and back button act on as soon as it is touched. [panel] restarts
 * the listener when a slot starts showing another panel; otherwise it would keep activating the old one.
 */
private fun Modifier.activateOnTouch(panel: PanelId, onActivate: () -> Unit): Modifier =
    pointerInput(panel) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) onActivate()
            }
        }
    }
