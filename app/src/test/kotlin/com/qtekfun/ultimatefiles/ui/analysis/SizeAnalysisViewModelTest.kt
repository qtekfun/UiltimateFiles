package com.qtekfun.ultimatefiles.ui.analysis

import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.domain.usecase.SizeAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SizeAnalysisViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var viewModel: SizeAnalysisViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        File(tmp.root, "sub/deep").mkdirs()
        File(tmp.root, "a.bin").writeBytes(ByteArray(10))
        File(tmp.root, "sub/b.bin").writeBytes(ByteArray(30))
        File(tmp.root, "sub/deep/c.bin").writeBytes(ByteArray(5))
        val repo = LocalFileSystemRepository(tmp.root, "Internal") { null }
        viewModel = SizeAnalysisViewModel(SizeAnalyzer(repo))
    }

    @After
    fun tearDown() {
        // Its coroutines resume on IO threads; one still running after resetMain() would fail the next test.
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun done(): SizeAnalysisState.Done = viewModel.state.first { it is SizeAnalysisState.Done } as SizeAnalysisState.Done

    @Test
    fun `an analysis ends showing the folder that was analysed`() = runTest {
        viewModel.start(tmp.root.path, "Internal")

        val state = done()

        assertEquals("Internal", state.label)
        assertEquals(1, state.trail.size)
        assertEquals(45L, state.current.bytes)
    }

    @Test
    fun `opening a folder goes down and up comes back until the top`() = runTest {
        viewModel.start(tmp.root.path, "Internal")
        val sub = done().current.children!!.first { it.item.name == "sub" }

        viewModel.open(sub)
        assertEquals(listOf("sub"), (viewModel.state.value as SizeAnalysisState.Done).trail.drop(1).map { it.item.name })
        val deep = (viewModel.state.value as SizeAnalysisState.Done).current.children!!.first { it.item.name == "deep" }
        viewModel.open(deep)
        assertEquals(3, (viewModel.state.value as SizeAnalysisState.Done).trail.size)

        assertTrue(viewModel.up())
        assertTrue(viewModel.up())
        assertEquals(1, (viewModel.state.value as SizeAnalysisState.Done).trail.size)
        assertFalse("at the top there is nowhere to go up to", viewModel.up())
    }

    @Test
    fun `files and entries of another folder cannot be opened`() = runTest {
        viewModel.start(tmp.root.path, "Internal")
        val root = done().current
        val file = root.children!!.first { it.item.name == "a.bin" }
        val deepInSub = root.children!!.first { it.item.name == "sub" }.children!!.first { it.item.name == "deep" }

        viewModel.open(file)
        viewModel.open(deepInSub)

        assertEquals(1, (viewModel.state.value as SizeAnalysisState.Done).trail.size)
    }

    @Test
    fun `resetting forgets the analysis and a missing folder fails`() = runTest {
        viewModel.start(tmp.root.path, "Internal")
        done()
        viewModel.reset()
        assertEquals(SizeAnalysisState.Idle, viewModel.state.value)

        viewModel.start(File(tmp.root, "missing").path, "Missing")
        val failed = viewModel.state.first { it is SizeAnalysisState.Failed } as SizeAnalysisState.Failed
        assertEquals("Missing", failed.label)
    }
}
