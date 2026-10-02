package com.qtekfun.fexplo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferProgressTest {
    private fun progress(done: Long, total: Long) = TransferProgress("f", done, total, 0, 1)

    @Test
    fun `fraction is clamped`() {
        assertEquals(0.5f, progress(50, 100).fraction)
        assertEquals(1f, progress(150, 100).fraction)
    }

    @Test
    fun `fraction is null for unknown total`() {
        assertNull(progress(10, 0).fraction)
    }
}
