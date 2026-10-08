package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.AnalysisProgress
import com.qtekfun.ultimatefiles.core.model.FileItem
import com.qtekfun.ultimatefiles.core.model.SizeAnalysis
import com.qtekfun.ultimatefiles.core.model.SizeNode
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Walks a folder through [FileSystemRepository] and works out how much room every folder inside it takes, to show where
 * the space went. Memory stays bounded: of each folder only the [maxChildren] biggest entries are kept as nodes and the
 * rest are folded into one, while every total stays exact.
 */
class SizeAnalyzer(
    private val repository: FileSystemRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxChildren: Int = MAX_CHILDREN,
    private val maxDepth: Int = MAX_DEPTH,
) {

    /**
     * Analyses the folder at [path]; [onProgress] is called at most every [PROGRESS_INTERVAL_MILLIS]. A folder that cannot
     * be listed counts as unreadable and adds nothing, it does not fail the analysis. Cancelling the coroutine stops it.
     */
    suspend fun analyze(path: String, onProgress: (AnalysisProgress) -> Unit = {}): Result<SizeAnalysis> {
        val root = repository.stat(path).getOrElse { return Result.failure(it) }
        if (!root.isDirectory) return Result.failure(IllegalArgumentException("Not a folder: $path"))
        return Result.success(Walk(onProgress).run(root))
    }

    private inner class Walk(private val onProgress: (AnalysisProgress) -> Unit) {
        private var files = 0
        private var bytes = 0L
        private var unreadable = 0
        private var lastReport: Long? = null

        suspend fun run(root: FileItem): SizeAnalysis {
            val node = folder(root, depth = 0)
            report(root.path, force = true)
            return SizeAnalysis(node, unreadable)
        }

        private suspend fun folder(item: FileItem, depth: Int): SizeNode {
            currentCoroutineContext().ensureActive()
            report(item.path)
            if (depth >= maxDepth) {
                // Probably a symbolic link loop: stop here and say so, instead of looping forever.
                unreadable++
                return SizeNode(item, bytes = 0, files = 0, children = emptyList())
            }
            val listed = repository.listFiles(item.path).getOrElse {
                currentCoroutineContext().ensureActive()
                unreadable++
                return SizeNode(item, bytes = 0, files = 0, children = emptyList())
            }
            val nodes = ArrayList<SizeNode>(listed.size)
            for (child in listed) {
                if (child.isDirectory) {
                    nodes += folder(child, depth + 1)
                } else {
                    files++
                    bytes += child.sizeBytes
                    nodes += SizeNode(child, child.sizeBytes, files = 1)
                    if (files % REPORT_CHECK_EVERY == 0) {
                        currentCoroutineContext().ensureActive()
                        report(item.path)
                    }
                }
            }
            val sorted = nodes.sortedWith(compareByDescending<SizeNode> { it.bytes }.thenBy { it.item.name.lowercase() })
            val kept = sorted.take(maxChildren)
            val rest = sorted.drop(maxChildren)
            val children = if (rest.isEmpty()) {
                kept
            } else {
                kept + SizeNode(
                    item = FileItem(item.path + OTHER_SUFFIX, name = "", isDirectory = false, sizeBytes = rest.sumOf { it.bytes }, lastModifiedMillis = 0L, mimeType = null),
                    bytes = rest.sumOf { it.bytes },
                    files = rest.sumOf { it.files },
                    children = null,
                    folded = rest.size,
                )
            }
            return SizeNode(item, bytes = sorted.sumOf { it.bytes }, files = sorted.sumOf { it.files }, children = children)
        }

        private fun report(folder: String, force: Boolean = false) {
            val now = clockMillis()
            val last = lastReport
            // Null until the first report: subtracting from a sentinel such as Long.MIN_VALUE would overflow.
            if (!force && last != null && now - last < PROGRESS_INTERVAL_MILLIS) return
            lastReport = now
            onProgress(AnalysisProgress(files, bytes, folder, unreadable))
        }
    }

    companion object {
        /** Whether a folder at [path] can be analysed: on this device (internal storage, USB, SD, SAF), not on a server or in an archive. */
        fun supports(path: String): Boolean =
            !(path.startsWith("dav://") || path.startsWith("sftp://") || path.startsWith("smb://") || ArchivePaths.isArchivePath(path))

        const val MAX_CHILDREN = 200
        const val MAX_DEPTH = 48
        const val PROGRESS_INTERVAL_MILLIS = 150L
        private const val REPORT_CHECK_EVERY = 256
        private const val OTHER_SUFFIX = "/\u0000other"
    }
}
