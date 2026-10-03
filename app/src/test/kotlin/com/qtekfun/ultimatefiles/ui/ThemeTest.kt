package com.qtekfun.ultimatefiles.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimatefiles.core.model.ThemeMode
import com.qtekfun.ultimatefiles.ui.theme.resolveDarkTheme
import com.qtekfun.ultimatefiles.ui.theme.toAmoled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `system mode follows the device, the others are fixed`() {
        assertTrue(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemDark = false))
        assertTrue(resolveDarkTheme(ThemeMode.AMOLED, systemDark = false))
    }

    @Test
    fun `amoled makes background and surface pure black and keeps the accent`() {
        val base = darkColorScheme(primary = Color(0xFF7FD4BE))
        val amoled = base.toAmoled()

        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(base.primary, amoled.primary)
    }
}
