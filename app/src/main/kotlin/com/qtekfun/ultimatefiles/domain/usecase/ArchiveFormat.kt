package com.qtekfun.ultimatefiles.domain.usecase

/** Archive types that can be unpacked. ZIP is also what compressing produces. */
enum class ArchiveFormat(private val suffixes: List<String>) {
    ZIP(listOf(".zip")),
    SEVEN_Z(listOf(".7z")),
    TAR(listOf(".tar")),
    TAR_GZ(listOf(".tar.gz", ".tgz")),
    ;

    /** [name] without this format's extension, e.g. `photos` for `photos.tar.gz`. */
    fun baseName(name: String): String {
        val suffix = suffixes.firstOrNull { name.endsWith(it, ignoreCase = true) } ?: return name
        return name.dropLast(suffix.length).ifEmpty { name }
    }

    companion object {
        fun of(name: String): ArchiveFormat? =
            entries.firstOrNull { format -> format.suffixes.any { name.endsWith(it, ignoreCase = true) } }
    }
}

/** The file is not an archive type this app can read. */
class ArchiveFormatNotSupported(name: String) : java.io.IOException("Unsupported archive: $name")
