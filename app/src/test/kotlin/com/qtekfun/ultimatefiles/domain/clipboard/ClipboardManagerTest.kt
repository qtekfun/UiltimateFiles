package com.qtekfun.ultimatefiles.domain.clipboard

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardManagerTest {
    private val item = FileItem("/a/b.txt", "b.txt", false, 1, 0, "text/plain")

    @Test
    fun `starts empty`() {
        assertNull(ClipboardManager().state.value)
    }

    @Test
    fun `copy and cut replace the buffer`() {
        val clipboard = ClipboardManager()
        clipboard.copy("/a", listOf(item))
        assertEquals(OperationType.COPY, clipboard.state.value?.operation)
        clipboard.cut("/a", listOf(item))
        assertEquals(OperationType.CUT, clipboard.state.value?.operation)
        assertEquals(listOf(item), clipboard.state.value?.items)
    }

    @Test
    fun `empty selection leaves buffer empty and clear empties it`() {
        val clipboard = ClipboardManager()
        clipboard.copy("/a", emptyList())
        assertNull(clipboard.state.value)
        clipboard.copy("/a", listOf(item))
        clipboard.clear()
        assertNull(clipboard.state.value)
    }
}
