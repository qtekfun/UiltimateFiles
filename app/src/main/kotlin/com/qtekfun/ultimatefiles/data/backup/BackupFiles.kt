package com.qtekfun.ultimatefiles.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads and writes the backup document through the document picker's URIs (no storage permission involved). */
class BackupFiles(private val context: Context) {
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
        stream.use { input ->
            // A backup is tiny; refuse anything big instead of loading a random file into memory.
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) throw IOException("Not a backup file")
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
