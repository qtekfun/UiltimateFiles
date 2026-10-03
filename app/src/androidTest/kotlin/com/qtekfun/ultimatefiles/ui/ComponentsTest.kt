package com.qtekfun.ultimatefiles.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.OperationType
import com.qtekfun.ultimatefiles.core.model.TransferSummary
import com.qtekfun.ultimatefiles.ui.components.CheckNotice
import com.qtekfun.ultimatefiles.ui.components.InterruptedTransfersDialog
import com.qtekfun.ultimatefiles.ui.components.NameInputDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests of the reusable pieces, run on a device or emulator: they need no storage permission, network or setup. */
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun nameDialogOnlyAcceptsUsableNames() {
        var confirmed: String? = null
        compose.setContent {
            MaterialTheme {
                NameInputDialog(title = R.string.action_new_folder, initialName = "", onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        val ok = compose.onNodeWithText(text(R.string.action_ok))
        ok.assertIsNotEnabled()

        compose.onNodeWithText(text(R.string.field_name)).performTextInput("a/b")
        ok.assertIsNotEnabled() // a slash cannot be part of a file name

        compose.onNodeWithText(text(R.string.field_name)).performTextReplacement("  Photos  ")
        ok.assertIsEnabled()
        ok.performClick()
        assertEquals("Photos", confirmed)
    }

    @Test
    fun checkNoticeShowsTheProblemAndItsFix() {
        var clicks = 0
        compose.setContent {
            MaterialTheme { CheckNotice(false, R.string.settings_battery_off, R.string.battery_hint_allow) { clicks++ } }
        }
        compose.onNodeWithText(text(R.string.settings_battery_off)).assertExists()
        compose.onNodeWithText(text(R.string.battery_hint_allow)).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun checkNoticeIsHiddenWhenAllIsWell() {
        compose.setContent {
            MaterialTheme { CheckNotice(true, R.string.settings_battery_off, R.string.battery_hint_allow) {} }
        }
        compose.onNodeWithText(text(R.string.settings_battery_off)).assertDoesNotExist()
    }

    @Test
    fun interruptedDialogOffersResumeAndDiscard() {
        var resumed = 0
        var discarded = 0
        val tasks = listOf(TransferSummary(OperationType.COPY, itemCount = 1, firstItemName = "movie.mkv"))
        compose.setContent {
            MaterialTheme { InterruptedTransfersDialog(tasks, onResume = { resumed++ }, onDiscard = { discarded++ }) }
        }
        compose.onNodeWithText(text(R.string.history_copying_one, "movie.mkv"), substring = true).assertExists()
        compose.onNodeWithText(text(R.string.interrupted_resume)).performClick()
        compose.onNodeWithText(text(R.string.interrupted_discard)).performClick()
        assertEquals(1, resumed)
        assertEquals(1, discarded)
    }
}
