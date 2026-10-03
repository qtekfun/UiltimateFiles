package com.qtekfun.fexplo.core.util

import com.qtekfun.fexplo.core.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindTest {
    private fun file(name: String, mime: String?, dir: Boolean = false) =
        FileItem(name, name, dir, 0, 0, mime)

    @Test
    fun `classifies by mime type`() {
        assertEquals(FileKind.FOLDER, file("d", null, dir = true).kind())
        assertEquals(FileKind.IMAGE, file("a.png", "image/png").kind())
        assertEquals(FileKind.VIDEO, file("a.mp4", "video/mp4").kind())
        assertEquals(FileKind.AUDIO, file("a.mp3", "audio/mpeg").kind())
        assertEquals(FileKind.PDF, file("a.pdf", "application/pdf").kind())
        assertEquals(FileKind.TEXT, file("a.txt", "text/plain").kind())
        assertEquals(FileKind.ARCHIVE, file("a.zip", "application/zip").kind())
        assertEquals(FileKind.APK, file("a.apk", "application/vnd.android.package-archive").kind())
        assertEquals(FileKind.OTHER, file("a.bin", "application/octet-stream").kind())
    }

    @Test
    fun `unknown mime without a resolvable extension is other`() {
        assertEquals(FileKind.OTHER, file("noext", null).kind())
    }
}
