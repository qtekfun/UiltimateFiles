package com.qtekfun.ultimatefiles.core.util

/**
 * Least-recently-used cache bounded by the summed [sizeOf] of its values rather than by their count
 * (bitmaps differ a lot in size). Thread-safe; `android.util.LruCache` does the same but is a stub in JVM unit tests.
 */
class SizedLruCache<K : Any, V : Any>(private val maxSize: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var size = 0L

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map.put(key, value)?.let { size -= sizeOf(it) }
        size += sizeOf(value)
        val eldest = map.entries.iterator()
        while (size > maxSize && map.size > 1 && eldest.hasNext()) {
            val entry = eldest.next()
            // The value just stored is the most recent one, so it is never the one dropped.
            size -= sizeOf(entry.value)
            eldest.remove()
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
        size = 0
    }

    @get:Synchronized
    val count: Int get() = map.size
}
