package com.qtekfun.ultimatefiles.ui.browser

import com.qtekfun.ultimatefiles.core.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemActionTest {
    private val file = FileItem("/storage/a.txt", "a.txt", false, 1, 0L, "text/plain")

    @Test
    fun selectIsTheFirstEntryOfTheMenu() {
        assertEquals(FileItemAction.SELECT, FileItemAction.entries.first())
    }

    @Test
    fun selectIsOfferedUnlessTheItemIsAlreadySelected() {
        assertTrue(FileItemAction.SELECT.appliesTo(file, selected = false))
        assertFalse(FileItemAction.SELECT.appliesTo(file, selected = true))
        assertTrue(FileItemAction.COPY.appliesTo(file, selected = true))
    }
}
