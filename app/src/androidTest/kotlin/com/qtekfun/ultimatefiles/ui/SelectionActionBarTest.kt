package com.qtekfun.ultimatefiles.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.ui.browser.SelectionActionBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectionActionBarTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val calls = mutableListOf<String>()

    private fun show(count: Int, canExtract: Boolean) = compose.setContent {
        MaterialTheme {
            SelectionActionBar(
                selectedCount = count,
                onClearSelection = { calls += "clear" },
                onCopy = { calls += "copy" },
                onCut = { calls += "cut" },
                onDelete = { calls += "delete" },
                onShare = { calls += "share" },
                onRename = { calls += "rename" },
                onProperties = { calls += "properties" },
                onCompress = { calls += "compress" },
                onExtract = { calls += "extract" },
                canExtract = canExtract,
            )
        }
    }

    @Test
    fun showsTheCountAndRunsTheDirectActions() {
        show(count = 3, canExtract = false)
        compose.onNodeWithText(text(R.string.selection_count, 3)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.action_copy)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_delete)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_clear_selection)).performClick()
        assertEquals(listOf("copy", "delete", "clear"), calls)
    }

    @Test
    fun singleItemActionsAreDisabledForSeveralItems() {
        show(count = 2, canExtract = false)
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        compose.onNodeWithText(text(R.string.action_rename)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_properties)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_compress)).assertIsEnabled()
    }

    @Test
    fun extractNeedsArchives() {
        show(count = 1, canExtract = false)
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        compose.onNodeWithText(text(R.string.action_extract)).assertIsNotEnabled()
    }

    @Test
    fun extractRunsForArchives() {
        show(count = 1, canExtract = true)
        compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        compose.onNodeWithText(text(R.string.action_extract)).performClick()
        assertEquals(listOf("extract"), calls)
    }
}
