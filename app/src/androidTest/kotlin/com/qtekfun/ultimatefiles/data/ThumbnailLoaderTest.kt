package com.qtekfun.ultimatefiles.data

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatefiles.core.datastore.DataStoreUserPreferencesRepository
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.data.repository.LocalFileSystemRepository
import com.qtekfun.ultimatefiles.data.thumbnail.ThumbnailLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThumbnailLoaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var loader: ThumbnailLoader

    @Before
    fun setUp() {
        val repository = LocalFileSystemRepository(tmp.root, "Internal") { null }
        val store = PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") }
        loader = ThumbnailLoader(
            InstrumentationRegistry.getInstrumentation().targetContext,
            repository,
            DataStoreUserPreferencesRepository(store),
        )
    }

    private fun picture(name: String, width: Int, height: Int, format: Bitmap.CompressFormat): File {
        val file = tmp.newFile(name)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> file.outputStream().use { bitmap.compress(format, 90, it) } }
        return file
    }

    private fun itemOf(file: File, mime: String) = FileItem(file.path, file.name, false, file.length(), file.lastModified(), mime)

    @Test
    fun aPhotoIsScaledDownKeepingItsShape() = runBlocking {
        val item = itemOf(picture("wide.png", 800, 400, Bitmap.CompressFormat.PNG), "image/png")

        val thumbnail = loader.load(item, 100)

        assertNotNull(thumbnail)
        assertTrue("shorter side at least the target", minOf(thumbnail!!.width, thumbnail.height) >= 100)
        assertTrue("smaller than the original", thumbnail.width < 800)
        assertEquals("aspect ratio kept", 2f, thumbnail.width.toFloat() / thumbnail.height, 0.1f)
        assertSame("second request comes from memory", thumbnail, loader.cached(item, 100))
    }

    @Test
    fun exifRotationIsApplied() = runBlocking {
        val file = picture("rotated.jpg", 400, 200, Bitmap.CompressFormat.JPEG)
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val thumbnail = loader.load(itemOf(file, "image/jpeg"), 100)

        assertNotNull(thumbnail)
        assertTrue("turned upright: taller than wide", thumbnail!!.height > thumbnail.width)
    }

    @Test
    fun aBrokenPictureGivesNothingInsteadOfCrashing() = runBlocking {
        val file = tmp.newFile("broken.jpg").apply { writeText("not a picture") }

        assertNull(loader.load(itemOf(file, "image/jpeg"), 100))
        assertNull("and it is not retried", loader.load(itemOf(file, "image/jpeg"), 100))
    }

    @Test
    fun otherFilesAndNetworkPhotosAreSkipped() = runBlocking {
        val text = tmp.newFile("a.txt").apply { writeText("hi") }
        assertNull(loader.load(itemOf(text, "text/plain"), 100))

        val remote = FileItem("dav://account/a.jpg", "a.jpg", false, 1_000, 0L, "image/jpeg")
        assertNull("network thumbnails are off by default", loader.load(remote, 100))
    }
}
