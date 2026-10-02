package com.qtekfun.fexplo.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UtilTest {
    @Test
    fun `uniqueName keeps free names and numbers taken ones before the extension`() {
        assertEquals("a.txt", uniqueName("a.txt") { false })
        assertEquals("a (1).txt", uniqueName("a.txt") { it == "a.txt" })
        assertEquals("a (2).txt", uniqueName("a.txt") { it == "a.txt" || it == "a (1).txt" })
        assertEquals("dir (1)", uniqueName("dir") { it == "dir" })
        assertEquals(".hidden (1)", uniqueName(".hidden") { it == ".hidden" })
    }

    @Test
    fun `formatBytes uses 1024 based units`() {
        assertEquals("0 B", formatBytes(0, Locale.US))
        assertEquals("1023 B", formatBytes(1023, Locale.US))
        assertEquals("1.0 KB", formatBytes(1024, Locale.US))
        assertEquals("1.5 MB", formatBytes(1_572_864, Locale.US))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024, Locale.US))
    }

    @Test
    fun `speed meter computes bytes per second over the window`() {
        var now = 0L
        val meter = TransferSpeedMeter({ now })
        assertEquals(0L, meter.record(0))
        now = 1_000
        assertEquals(1_000L, meter.record(1_000))
        now = 2_000
        assertEquals(1_500L, meter.record(3_000))
    }
}
