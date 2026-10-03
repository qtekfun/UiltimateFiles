package com.qtekfun.fexplo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.fexplo.domain.history.HistoryEntry
import com.qtekfun.fexplo.domain.history.TransferHistoryRepository
import com.qtekfun.fexplo.domain.transfer.TransferCoordinator
import com.qtekfun.fexplo.domain.transfer.TransferState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val history: TransferHistoryRepository,
    private val coordinator: TransferCoordinator,
) : ViewModel() {

    /** The running batch and the queued ones, shown above the finished entries. */
    val transfer: StateFlow<TransferState> = coordinator.state

    fun togglePause() = coordinator.togglePause()

    fun cancelRunning() = coordinator.cancelAll()


    val entries: StateFlow<List<HistoryEntry>> = history.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { history.clear() }
    }
}
