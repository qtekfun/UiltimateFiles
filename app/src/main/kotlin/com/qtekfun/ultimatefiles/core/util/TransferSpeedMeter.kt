package com.qtekfun.ultimatefiles.core.util

/** Rolling-window throughput estimate fed with the cumulative number of bytes transferred. */
class TransferSpeedMeter(
    private val clockMillis: () -> Long,
    private val windowMillis: Long = 2_000,
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** Records [totalBytes] at the current time and returns bytes per second (0 until two samples exist). */
    fun record(totalBytes: Long): Long {
        val now = clockMillis()
        samples.addLast(now to totalBytes)
        while (samples.size > 2 && now - samples.first().first > windowMillis) {
            samples.removeFirst()
        }
        val (firstTime, firstBytes) = samples.first()
        val elapsed = now - firstTime
        return if (elapsed > 0) (totalBytes - firstBytes) * 1_000 / elapsed else 0L
    }
}
