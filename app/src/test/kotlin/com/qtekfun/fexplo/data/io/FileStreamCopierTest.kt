package com.qtekfun.fexplo.data.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class FileStreamCopierTest {
    @Test
    fun `copies every byte and reports chunks`() = runTest {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val chunks = mutableListOf<Long>()

        val copied = FileStreamCopier(bufferSize = 4_096)
            .copy(ByteArrayInputStream(data), output) { chunks += it }

        assertEquals(10_000L, copied)
        assertArrayEquals(data, output.toByteArray())
        assertEquals(listOf(4_096L, 4_096L, 1_808L), chunks)
    }

    @Test
    fun `empty input copies nothing`() = runTest {
        val output = ByteArrayOutputStream()
        assertEquals(0L, FileStreamCopier().copy(ByteArrayInputStream(ByteArray(0)), output) {})
        assertEquals(0, output.size())
    }
}
