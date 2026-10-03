package com.qtekfun.ultimatefiles.data.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.qtekfun.ultimatefiles.domain.usecase.ArchiveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Archives other apps hand to UltimateFiles ("open with"). Another app's `content://` URI is only readable through
 * the resolver, so the archive is copied to the cache and then browsed like any local file.
 */
class IncomingFiles(private val context: Context) {
    private val _pending = MutableStateFlow<String?>(null)

    /** Local path of an archive that arrived and has not been shown yet. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    suspend fun accept(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val name = withExtension(displayName(uri), context.contentResolver.getType(uri))
            if (ArchiveFormat.of(name) == null) throw IOException("Not an archive: $name")
            val root = File(context.cacheDir, "incoming").apply { mkdirs() }
            val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
            root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
            val target = File(File(root, UUID.randomUUID().toString()).apply { mkdirs() }, name)
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            _pending.value = target.path
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun consume() {
        _pending.value = null
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it.substringAfterLast('/') }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "archive"
    }

    /** Apps often send a name without a usable extension; the MIME type then says what it is. */
    private fun withExtension(name: String, mime: String?): String {
        if (ArchiveFormat.of(name) != null) return name
        val extension = when (mime?.lowercase()) {
            "application/zip", "application/x-zip-compressed" -> ".zip"
            "application/x-7z-compressed" -> ".7z"
            "application/x-tar" -> ".tar"
            "application/gzip", "application/x-gzip", "application/x-gtar", "application/x-compressed-tar" -> ".tar.gz"
            else -> return name
        }
        return name + extension
    }

    private companion object {
        const val MAX_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
