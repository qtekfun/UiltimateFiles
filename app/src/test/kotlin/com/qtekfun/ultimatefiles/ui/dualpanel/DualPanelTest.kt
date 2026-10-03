package com.qtekfun.ultimatefiles.ui.dualpanel

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.PanelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPanelTest {
    private fun item(path: String) = FileItem(path, path.substringAfterLast('/'), false, 1, 0, null)

    @Test
    fun `split layout starts at 600dp`() {
        assertFalse(isSplitLayout(411))
        assertFalse(isSplitLayout(599))
        assertTrue(isSplitLayout(600))
        assertTrue(isSplitLayout(915))
    }

    @Test
    fun `drop on another folder becomes a pending drop`() {
        val state = DragDropState()
        state.start(DragPayload(listOf(item("/a/x.txt")), PanelId.LEFT, "/a"))

        assertTrue(state.requestDrop("/b"))

        assertEquals(PendingDrop(listOf(item("/a/x.txt")), "/b"), state.pendingDrop.value)
        assertNull(state.payload.value)
    }

    @Test
    fun `dropping back on the source folder or on an item itself is ignored`() {
        val state = DragDropState()
        state.start(DragPayload(listOf(item("/a/x")), PanelId.LEFT, "/a"))
        assertFalse(state.requestDrop("/a"))
        state.start(DragPayload(listOf(item("/a/x")), PanelId.LEFT, "/a"))
        assertFalse(state.requestDrop("/a/x"))
        assertNull(state.pendingDrop.value)
    }

    @Test
    fun `drop without a drag is ignored`() {
        assertFalse(DragDropState().requestDrop("/b"))
    }
}
