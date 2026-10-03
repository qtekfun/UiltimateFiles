package com.qtekfun.ultimatefiles.data.network

import java.io.File
import java.io.IOException

/**
 * What is remembered about an unfinished chunked upload: the server-side upload id and the SHA-256 of every chunk
 * already sent, in order. A resumed upload only skips a chunk whose hash matches the data being read again.
 */
data class UploadRecord(
    val uploadId: String,
    val chunkSize: Int,
    val chunkHashes: List<String>,
    val savedAtMillis: Long,
)

/** Persists [UploadRecord]s so a copy interrupted by the app dying can continue instead of starting over. */
interface UploadResumeStore {
    fun load(key: String): UploadRecord?

    fun save(key: String, record: UploadRecord)

    fun clear(key: String)
}

/**
 * File-backed [UploadResumeStore], one line per upload. Entries older than [maxAgeMillis] are ignored because the
 * server discards stale uploads (Nextcloud cleans them up after about a day).
 */
class FileUploadResumeStore(
    private val file: File,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = 20L * 60 * 60 * 1000,
) : UploadResumeStore {
    private val lock = Any()

    override fun load(key: String): UploadRecord? = synchronized(lock) {
        readAll()[key]?.takeIf { clockMillis() - it.savedAtMillis <= maxAgeMillis }
    }

    override fun save(key: String, record: UploadRecord) = synchronized(lock) {
        val all = readAll().filterValues { clockMillis() - it.savedAtMillis <= maxAgeMillis }.toMutableMap()
        all[key] = record
        writeAll(all)
    }

    override fun clear(key: String) = synchronized(lock) {
        val all = readAll()
        if (all.remove(key) != null) writeAll(all)
    }

    private fun readAll(): MutableMap<String, UploadRecord> {
        val result = linkedMapOf<String, UploadRecord>()
        if (!file.exists()) return result
        file.readLines().forEach { line ->
            val f = line.split('\t')
            if (f.size < 5) return@forEach
            val chunkSize = f[2].toIntOrNull() ?: return@forEach
            val savedAt = f[3].toLongOrNull() ?: return@forEach
            val hashes = if (f[4].isEmpty()) emptyList() else f[4].split(',')
            result[f[0]] = UploadRecord(f[1], chunkSize, hashes, savedAt)
        }
        return result
    }

    private fun writeAll(all: Map<String, UploadRecord>) {
        val text = all.entries.joinToString("\n") { (key, r) ->
            listOf(key, r.uploadId, r.chunkSize.toString(), r.savedAtMillis.toString(), r.chunkHashes.joinToString(",")).joinToString("\t")
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Cannot save upload state")
        }
    }
}
