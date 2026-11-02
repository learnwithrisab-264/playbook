package com.sourabh.jarvistube.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeRulesTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, india).toInstant().toEpochMilli()

    @Test fun tomorrowIsTheNextMidnight() {
        assertEquals(at(2026, 11, 3, 0), nextMidnightAfter(at(2026, 11, 2, 23, 59), india))
        assertEquals(at(2026, 11, 3, 0), nextMidnightAfter(at(2026, 11, 2, 0, 1), india))
    }

    @Test fun tomorrowCrossesTheMonthEnd() {
        assertEquals(at(2026, 12, 1, 0), nextMidnightAfter(at(2026, 11, 30, 18), india))
    }

    @Test fun timeLeftReadsNaturally() {
        assertEquals("40 sec", timeLeftText(40_000))
        assertEquals("1 min", timeLeftText(60_000))
        assertEquals("15 min", timeLeftText(14 * 60_000L + 1))
        assertEquals("2 h 5 min", timeLeftText(125 * 60_000L))
        assertEquals("0 sec", timeLeftText(-5))
    }

    @Test fun historyGroupsFollowCalendarDays() {
        val now = at(2026, 11, 2, 0, 30)
        assertEquals(HistoryDay.Today, historyDayOf(at(2026, 11, 2, 0, 10), now, india))
        // 40 minutes ago, but on the previous calendar day.
        assertEquals(HistoryDay.Yesterday, historyDayOf(at(2026, 11, 1, 23, 50), now, india))
        assertEquals(HistoryDay.Older, historyDayOf(at(2026, 10, 31, 23, 50), now, india))
    }
}
