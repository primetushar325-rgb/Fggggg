package com.gamesoundpro.app.utils

import kotlin.math.roundToInt

/** Small pure formatting helpers (unit-tested). */
object Format {

    /** 65000 -> "1:05"; 7000 -> "0:07"; 0 -> "0:00" */
    fun duration(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /** Recording timer style: 00:07 */
    fun clock(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** 245_760_000 -> "234.4 MB"; 5300 -> "5.2 KB"; 900 -> "900 B" */
    fun bytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    /** 0.72 -> "72%" */
    fun percent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%"
}
