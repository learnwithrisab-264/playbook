package com.sourabh.jarvistube.lock

/** The longest session you can start, so you can never lock yourself out for long by mistake. */
const val MAX_LOCK_MINUTES = 180

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

/** Rounded up, so the last seconds read "1 min left", never "0 min left". */
fun minutesLeftText(ms: Long): String = "${(ms.coerceAtLeast(0L) + 59_999L) / 60_000L} min left"
