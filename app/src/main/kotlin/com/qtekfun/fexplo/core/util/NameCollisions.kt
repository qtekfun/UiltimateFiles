package com.qtekfun.fexplo.core.util

/** Returns [name] if free, else `base (1).ext`, `base (2).ext`… until [isTaken] says it is free. */
fun uniqueName(name: String, isTaken: (String) -> Boolean): String {
    if (!isTaken(name)) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    var counter = 1
    while (true) {
        val candidate = "$base ($counter)$extension"
        if (!isTaken(candidate)) return candidate
        counter++
    }
}
