package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.FileHashes
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Computes MD5 and SHA-256 of a file in a single pass; cancellable and off the main thread. */
class HashCalcUseCase(private val repository: FileSystemRepository) {

    suspend operator fun invoke(item: FileItem): Result<FileHashes> = withContext(Dispatchers.IO) {
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha256 = MessageDigest.getInstance("SHA-256")
            repository.openInput(item).getOrThrow().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    md5.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                }
            }
            Result.success(FileHashes(md5.digest().toHexString(), sha256.digest().toHexString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
