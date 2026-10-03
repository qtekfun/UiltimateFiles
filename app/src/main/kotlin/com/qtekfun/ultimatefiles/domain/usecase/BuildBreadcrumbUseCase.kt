package com.qtekfun.ultimatefiles.domain.usecase

import com.qtekfun.ultimatefiles.core.model.BreadcrumbSegment
import com.qtekfun.ultimatefiles.domain.repository.FileSystemRepository

/** Resolves the chain volume root → ... → [path] using only [FileSystemRepository] (works for any backend). */
class BuildBreadcrumbUseCase(private val repository: FileSystemRepository) {

    suspend operator fun invoke(path: String): List<BreadcrumbSegment> {
        val volumeLabels = repository.volumes().associate { it.rootPath to it.label }
        val segments = ArrayDeque<BreadcrumbSegment>()
        var current: String? = path
        var depth = 0
        while (current != null && depth++ < MAX_DEPTH) {
            val label = volumeLabels[current] ?: repository.stat(current).getOrNull()?.name ?: break
            segments.addFirst(BreadcrumbSegment(label, current))
            current = repository.parentOf(current)
        }
        return segments.toList()
    }

    private companion object {
        const val MAX_DEPTH = 64
    }
}
