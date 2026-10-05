package com.qtekfun.ultimatefiles.data.thumbnail

import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.util.SizedLruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPolicyTest {
    private fun file(path: String, mime: String, size: Long = 1_000, directory: Boolean = false) =
        FileItem(path, path.substringAfterLast('/'), directory, size, 0L, mime)

    @Test
    fun photosAndVideosOnLocalAndSafStorageGetThumbnails() {
        assertTrue(ThumbnailPolicy.canLoad(file("/storage/emulated/0/a.jpg", "image/jpeg"), allowNetwork = false))
        assertTrue(ThumbnailPolicy.canLoad(file("/storage/emulated/0/a.mp4", "video/mp4"), allowNetwork = false))
        assertTrue(ThumbnailPolicy.canLoad(file("content://tree/doc/a.png", "image/png"), allowNetwork = false))
    }

    @Test
    fun otherKindsAndFoldersDoNot() {
        assertFalse(ThumbnailPolicy.mayHaveThumbnail(file("/a.pdf", "application/pdf")))
        assertFalse(ThumbnailPolicy.mayHaveThumbnail(file("/a.mp3", "audio/mpeg")))
        assertFalse(ThumbnailPolicy.mayHaveThumbnail(file("/DCIM", "image/jpeg", directory = true)))
    }

    @Test
    fun picturesInsideArchivesDoNot() {
        val inside = file("archive://%2Fa.zip!/photo.jpg", "image/jpeg")
        assertEquals(ThumbnailPolicy.Source.ARCHIVE, ThumbnailPolicy.sourceOf(inside.path))
        assertFalse(ThumbnailPolicy.mayHaveThumbnail(inside))
        assertFalse(ThumbnailPolicy.canLoad(inside, allowNetwork = true))
    }

    @Test
    fun networkNeedsTheSettingAndIsLimitedToSmallPhotos() {
        val photo = file("dav://acc/a.jpg", "image/jpeg", size = 2_000_000)
        assertFalse(ThumbnailPolicy.canLoad(photo, allowNetwork = false))
        assertTrue(ThumbnailPolicy.canLoad(photo, allowNetwork = true))
        assertFalse(ThumbnailPolicy.canLoad(photo.copy(sizeBytes = ThumbnailPolicy.NETWORK_IMAGE_MAX_BYTES + 1), allowNetwork = true))
        assertFalse(ThumbnailPolicy.canLoad(photo.copy(sizeBytes = 0), allowNetwork = true))
        assertFalse(ThumbnailPolicy.canLoad(file("sftp://acc/a.mp4", "video/mp4"), allowNetwork = true))
        // The row is still laid out as a picture so it does not jump if the setting is turned on.
        assertTrue(ThumbnailPolicy.mayHaveThumbnail(photo))
    }

    @Test
    fun sampleSizeKeepsTheShorterSideAtLeastTheTarget() {
        assertEquals(1, ThumbnailPolicy.sampleSize(200, 150, 168))
        assertEquals(8, ThumbnailPolicy.sampleSize(4000, 3000, 336))
        assertEquals(1, ThumbnailPolicy.sampleSize(0, 0, 100))
        // 3000/8 = 375 >= 336 but 3000/16 = 187 < 336
        assertEquals(8, ThumbnailPolicy.sampleSize(4000, 3000, 336))
    }

    @Test
    fun scaledSizeKeepsTheAspectRatioAndNeverUpscales() {
        assertEquals(168 * 16 / 9 to 168, ThumbnailPolicy.scaledSize(1920, 1080, 168))
        assertEquals(168 to 168 * 16 / 9, ThumbnailPolicy.scaledSize(1080, 1920, 168))
        assertNull(ThumbnailPolicy.scaledSize(100, 80, 168))
        assertNull(ThumbnailPolicy.scaledSize(0, 0, 168))
    }

    @Test
    fun cacheDropsTheLeastRecentlyUsedWhenFull() {
        val cache = SizedLruCache<String, ByteArray>(10) { it.size.toLong() }
        cache["a"] = ByteArray(4)
        cache["b"] = ByteArray(4)
        cache["a"] // a becomes the most recent
        cache["c"] = ByteArray(4)

        assertNull(cache["b"])
        assertTrue(cache["a"] != null && cache["c"] != null)
        assertEquals(2, cache.count)
    }

    @Test
    fun cacheKeepsASingleValueBiggerThanItsLimit() {
        val cache = SizedLruCache<String, ByteArray>(2) { it.size.toLong() }
        cache["big"] = ByteArray(5)
        assertTrue(cache["big"] != null)
    }
}
