package com.sourabh.jarvistube.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import java.util.Locale

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatClock(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

fun compact(n: Long): String {
    val (v, suffix) = when {
        n >= 1_000_000_000 -> n / 1e9 to "B"
        n >= 1_000_000 -> n / 1e6 to "M"
        n >= 1_000 -> n / 1e3 to "K"
        else -> return n.toString()
    }
    val t = if (v >= 100) String.format(Locale.US, "%.0f", v)
    else String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    return t + suffix
}

/** "2022-06-21T07:38:31-07:00" -> "21 Jun 2022"; relative strings ("3 years ago") pass through. */
fun prettyDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(raw)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    } catch (e: Exception) {
        raw
    }
}
