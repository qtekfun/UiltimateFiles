package com.qtekfun.ultimatefiles.core.util

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.SortField
import com.qtekfun.ultimatefiles.core.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortingTest {
    private fun item(name: String, dir: Boolean = false, size: Long = 0, date: Long = 0) =
        FileItem(path = name, name = name, isDirectory = dir, sizeBytes = size, lastModifiedMillis = date, mimeType = null)

    private val items = listOf(
        item("b.txt", size = 10, date = 3),
        item("A.txt", size = 30, date = 1),
        item("zdir", dir = true),
        item("c.txt", size = 20, date = 2),
    )

    @Test
    fun `directories come first and names sort case-insensitively`() {
        val result = items.sortedByOrder(SortOrder(SortField.NAME)).map { it.name }
        assertEquals(listOf("zdir", "A.txt", "b.txt", "c.txt"), result)
    }

    @Test
    fun `size descending keeps directories first`() {
        val result = items.sortedByOrder(SortOrder(SortField.SIZE, ascending = false)).map { it.name }
        assertEquals(listOf("zdir", "A.txt", "c.txt", "b.txt"), result)
    }

    @Test
    fun `date ascending`() {
        val result = items.sortedByOrder(SortOrder(SortField.DATE)).map { it.name }
        assertEquals(listOf("zdir", "A.txt", "c.txt", "b.txt"), result)
    }
}
