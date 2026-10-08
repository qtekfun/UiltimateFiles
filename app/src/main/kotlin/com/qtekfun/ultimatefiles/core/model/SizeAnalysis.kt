package com.qtekfun.ultimatefiles.core.model

/**
 * One entry of a size analysis: a file, a folder, or (when [folded] is not zero) the single node that stands for the
 * [folded] smallest entries of a folder that had more than the analysis keeps.
 *
 * [bytes] and [files] are exact totals of everything below the node, whether or not it is kept in [children]
 * (`null` for a file or a node that is not a folder).
 */
data class SizeNode(
    val item: FileItem,
    val bytes: Long,
    val files: Int,
    val children: List<SizeNode>? = null,
    val folded: Int = 0,
) {
    val isOther: Boolean get() = folded > 0
}

/** Live figures of a running analysis. */
data class AnalysisProgress(
    val files: Int,
    val bytes: Long,
    val currentFolder: String,
    val unreadable: Int,
)

/** What an analysis found: the tree and how many folders could not be read (so their sizes are missing). */
data class SizeAnalysis(val root: SizeNode, val unreadable: Int)
