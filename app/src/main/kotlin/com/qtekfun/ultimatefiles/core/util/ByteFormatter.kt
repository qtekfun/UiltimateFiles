package com.qtekfun.ultimatefiles.core.util

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

/** `m:ss` below one hour, `h:mm:ss` above; locale independent. */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
