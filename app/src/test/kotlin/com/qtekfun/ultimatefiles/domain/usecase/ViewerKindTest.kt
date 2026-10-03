package com.qtekfun.ultimatefiles.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerKindTest {
    @Test
    fun `picks the viewer from the MIME type`() {
        assertEquals(ViewerKind.IMAGE, ViewerKind.of("a.jpg", "image/jpeg"))
        assertEquals(ViewerKind.PDF, ViewerKind.of("a.pdf", "application/pdf"))
        assertEquals(ViewerKind.MEDIA, ViewerKind.of("a.mp4", "video/mp4"))
        assertEquals(ViewerKind.MEDIA, ViewerKind.of("a.mp3", "audio/mpeg"))
        assertEquals(ViewerKind.TEXT, ViewerKind.of("a.txt", "text/plain"))
    }

    @Test
    fun `falls back to the extension when the MIME type is missing or generic`() {
        assertEquals(ViewerKind.TEXT, ViewerKind.of("Main.KT", null))
        assertEquals(ViewerKind.TEXT, ViewerKind.of("config.json", "application/octet-stream"))
    }

    @Test
    fun `leaves everything else to other apps`() {
        assertNull(ViewerKind.of("app.apk", "application/vnd.android.package-archive"))
        assertNull(ViewerKind.of("logo.svg", "image/svg+xml"))
        assertNull(ViewerKind.of("archive.zip", "application/zip"))
        assertNull(ViewerKind.of("noextension", null))
    }
}
