package com.qtekfun.fexplo.data.repository

/** Escaping for tab-separated, line-per-record files: `\t`, `\n`, `\r` and `\` never appear raw inside a field. */
internal object Tsv {
    /** Marker for a null field; cannot clash with [escape] output, which never yields a lone `\N`. */
    const val NULL = "\\N"

    fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(text[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
