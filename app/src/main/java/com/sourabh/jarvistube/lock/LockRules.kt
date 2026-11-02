package com.sourabh.jarvistube.lock

/** The longest session you can start, so you can never lock yourself out for long by mistake. */
const val MAX_LOCK_MINUTES = 180

/** Typing this and waiting [EXIT_WAIT_SECONDS] uses the emergency exit. */
const val EXIT_PHRASE = "END MY STUDY SESSION"
const val EXIT_WAIT_SECONDS = 10
const val EXITS_PER_WEEK = 3
private const val EXIT_DAY_MS = 86_400_000L
private const val EXIT_WEEK_MS = 7 * EXIT_DAY_MS

/** Calls, emergency help, alarms and the phone's own system screens keep working during a session. */
private val ALWAYS_ALLOWED = setOf(
    "android", "com.android.systemui",
    "com.android.server.telecom", "com.android.phone", "com.android.incallui", "com.android.emergency", "com.oplus.sos",
    "com.google.android.deskclock", "com.coloros.alarmclock"
)

/** Everything else is blocked, Settings and App info included. [keyboards] stay for the dialer's search. */
fun lockAllows(pkg: String, own: String, dialer: String?, keyboards: Set<String>): Boolean =
    pkg == own || pkg == dialer || pkg in ALWAYS_ALLOWED || pkg in keyboards

/**
 * Time left in a session. [byClock] is end time minus now. After a restart with no internet the
 * phone's clock can't be trusted, so [floor], the time left when last saved and counted down
 * since, is used if it is longer: moving the clock forward can't shorten a session.
 */
fun lockLeftMs(byClock: Long, floor: Long, clockTrusted: Boolean): Long =
    (if (clockTrusted) byClock else maxOf(byClock, floor)).coerceAtLeast(0L)

fun exitReady(typed: String, secondsWaited: Int): Boolean =
    secondsWaited >= EXIT_WAIT_SECONDS && typed.trim().equals(EXIT_PHRASE, ignoreCase = true)

/** Uses still counted against the weekly limit. */
fun recentExits(uses: List<Long>, now: Long): List<Long> = uses.filter { now - it < EXIT_WEEK_MS }.sorted()

/** How long until the exit can be used, 0 if it can be now: once in 24 hours, 3 times in 7 days. */
fun exitWaitMs(uses: List<Long>, now: Long): Long {
    val recent = recentExits(uses, now)
    val afterDay = recent.lastOrNull()?.let { it + EXIT_DAY_MS - now } ?: 0L
    val afterWeek = if (recent.size >= EXITS_PER_WEEK) recent[recent.size - EXITS_PER_WEEK] + EXIT_WEEK_MS - now else 0L
    return maxOf(afterDay, afterWeek, 0L)
}

/** Rounded up, so the last seconds read "1 min left", never "0 min left". */
fun minutesLeftText(ms: Long): String = "${(ms.coerceAtLeast(0L) + 59_999L) / 60_000L} min left"
