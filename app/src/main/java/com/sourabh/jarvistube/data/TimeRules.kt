package com.sourabh.jarvistube.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The start of the next day in [zone]: when an "until tomorrow" block ends. */
fun nextMidnightAfter(nowMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** Short "time left" text: "2 h 5 min", "12 min", "40 sec". Rounds up, so it never reads 0 too early. */
fun timeLeftText(ms: Long): String {
    val sec = (ms.coerceAtLeast(0L) + 999) / 1000
    if (sec < 60) return "$sec sec"
    val min = (sec + 59) / 60
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}

enum class HistoryDay(val label: String) { Today("Today"), Yesterday("Yesterday"), Older("Older") }

/** Which History group a video watched at [watchedAt] belongs to, by calendar day in [zone]. */
fun historyDayOf(watchedAt: Long, nowMs: Long, zone: ZoneId): HistoryDay {
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(watchedAt).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    )
    return when {
        days <= 0L -> HistoryDay.Today
        days == 1L -> HistoryDay.Yesterday
        else -> HistoryDay.Older
    }
}
