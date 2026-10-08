package com.qtekfun.ultimatefiles.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.core.model.AnalysisProgress
import com.qtekfun.ultimatefiles.core.model.SizeAnalysis
import com.qtekfun.ultimatefiles.core.model.SizeNode
import com.qtekfun.ultimatefiles.domain.usecase.SizeAnalyzer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SizeAnalysisState {
    data object Idle : SizeAnalysisState
    data class Scanning(val path: String, val label: String, val progress: AnalysisProgress? = null) : SizeAnalysisState

    /** [trail] runs from the analysed folder (first) down to the folder being shown (last). */
    data class Done(val path: String, val label: String, val analysis: SizeAnalysis, val trail: List<SizeNode>) : SizeAnalysisState {
        val current: SizeNode get() = trail.last()
    }

    data class Failed(val path: String, val label: String, val message: String?) : SizeAnalysisState
}

/** Runs one analysis at a time and keeps where the user is in its tree. */
class SizeAnalysisViewModel(
    private val analyzer: SizeAnalyzer,
    /** The walk is CPU work between listings, so it stays off the main thread. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<SizeAnalysisState>(SizeAnalysisState.Idle)
    val state: StateFlow<SizeAnalysisState> = _state.asStateFlow()

    private var job: Job? = null

    /** Analyses the folder at [path], called [label] on screen, replacing whatever was running or shown. */
    fun start(path: String, label: String) {
        job?.cancel()
        _state.value = SizeAnalysisState.Scanning(path, label)
        job = viewModelScope.launch(dispatcher) {
            val result = analyzer.analyze(path) { progress ->
                _state.update { if (it is SizeAnalysisState.Scanning && it.path == path) it.copy(progress = progress) else it }
            }
            _state.value = result.fold(
                onSuccess = { SizeAnalysisState.Done(path, label, it, listOf(it.root)) },
                onFailure = { SizeAnalysisState.Failed(path, label, it.message) },
            )
        }
    }

    /** Stops a running analysis and forgets everything. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = SizeAnalysisState.Idle
    }

    /** Goes down into [node] when it is a folder of the one being shown. */
    fun open(node: SizeNode) {
        val done = _state.value as? SizeAnalysisState.Done ?: return
        if (!node.item.isDirectory || node.isOther || node.children == null) return
        if (done.current.children?.contains(node) != true) return
        _state.value = done.copy(trail = done.trail + node)
    }

    /** Goes up one level; false when already at the analysed folder, so the caller leaves the screen. */
    fun up(): Boolean {
        val done = _state.value as? SizeAnalysisState.Done ?: return false
        if (done.trail.size <= 1) return false
        _state.value = done.copy(trail = done.trail.dropLast(1))
        return true
    }
}
