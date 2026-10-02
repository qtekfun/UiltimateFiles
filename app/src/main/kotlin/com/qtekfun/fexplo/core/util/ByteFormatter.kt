package com.qtekfun.fexplo.core.util

import java.util.Locale

private val UNITS = arrayOf("KB", "MB", "GB", "TB")

/** Human readable size using 1024-based units, e.g. `1.5 MB`. */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024
        unit++
    } while (value >= 1024 && unit < UNITS.lastIndex)
    return String.format(locale, "%.1f %s", value, UNITS[unit])
}
