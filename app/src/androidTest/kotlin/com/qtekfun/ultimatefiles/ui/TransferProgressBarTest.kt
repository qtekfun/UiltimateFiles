package com.qtekfun.ultimatefiles.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.TransferProgress
import com.qtekfun.ultimatefiles.core.model.TransferStatus
import com.qtekfun.ultimatefiles.ui.components.TransferProgressBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The bar must keep its two buttons on one line each, whatever the name and the figures. */
class TransferProgressBarTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val running = TransferProgress(
        currentName = "a-very-long-holiday-video-name-that-does-not-fit-in-a-narrow-bar-2026-final-cut.mp4",
        processedBytes = 400_000_000,
        totalBytes = 900_000_000,
        processedFiles = 1,
        totalFiles = 3,
        bytesPerSecond = 123_456_789,
        status = TransferStatus.VERIFYING,
    )

    @Test
    fun buttonsStayOnOneLineInANarrowBarWithALongNameAndFigures() {
        compose.setContent { TransferProgressBar(running, paused = false, onTogglePause = {}, onCancel = {}, modifier = Modifier.width(320.dp)) }

        // A label on a single line is about 20dp tall; wrapped onto several lines it would be much more.
        for (label in listOf(R.string.transfer_pause, R.string.transfer_cancel)) {
            val node = compose.onNodeWithText(text(label)).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$label wrapped", bounds.bottom - bounds.top <= 30.dp)
        }
    }

    @Test
    fun theButtonsPauseAndCancel() {
        var paused = false
        var cancelled = false
        compose.setContent {
            TransferProgressBar(running, paused = paused, onTogglePause = { paused = !paused }, onCancel = { cancelled = true })
        }

        compose.onNodeWithText(text(R.string.transfer_pause)).performClick()
        compose.onNodeWithText(text(R.string.transfer_cancel)).performClick()

        assertEquals(true, paused)
        assertEquals(true, cancelled)
    }
}
