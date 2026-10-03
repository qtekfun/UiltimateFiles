package com.qtekfun.ultimatefiles.domain.usecase

/** What the built-in viewer can show; everything else is handed to other apps. */
enum class ViewerKind {
    IMAGE,
    TEXT,
    PDF,
    MEDIA,
    ;

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "log", "json", "xml", "csv", "tsv", "yml", "yaml", "toml", "ini", "conf", "cfg", "properties",
            "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "sh", "c", "h", "cpp", "rs", "go", "gradle", "sql",
        )

        /** [mime] may be null or generic; the file extension then decides. */
        fun of(name: String, mime: String?): ViewerKind? {
            val extension = name.substringAfterLast('.', "").lowercase()
            val type = mime?.lowercase().orEmpty()
            return when {
                type == "image/svg+xml" || extension == "svg" -> null
                type.startsWith("image/") -> IMAGE
                type == "application/pdf" -> PDF
                type.startsWith("audio/") || type.startsWith("video/") -> MEDIA
                type.startsWith("text/") || extension in TEXT_EXTENSIONS -> TEXT
                else -> null
            }
        }
    }
}
