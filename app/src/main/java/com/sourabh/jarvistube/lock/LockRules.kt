package com.sourabh.jarvistube.lock

/** Typing this, and waiting [EXIT_WAIT_SECONDS], ends a session early. */
const val EXIT_PHRASE = "END MY STUDY SESSION"
const val EXIT_WAIT_SECONDS = 10

/** Calls, emergency help, alarms and the phone's own system screens keep working during a session. */
private val ALWAYS_ALLOWED = setOf(
    "android", "com.android.systemui",
    "com.android.server.telecom", "com.android.phone", "com.android.incallui", "com.android.emergency", "com.oplus.sos",
    "com.google.android.deskclock", "com.coloros.alarmclock"
)

/** Whether [pkg] may be on screen during a session. [keyboards] stay so the exit phrase can be typed. */
fun lockAllows(pkg: String, own: String, dialer: String?, keyboards: Set<String>): Boolean =
    pkg == own || pkg == dialer || pkg in ALWAYS_ALLOWED || pkg in keyboards

fun exitReady(typed: String, secondsWaited: Int): Boolean =
    secondsWaited >= EXIT_WAIT_SECONDS && typed.trim().equals(EXIT_PHRASE, ignoreCase = true)

/** Rounded up, so the last seconds read "1 min left", never "0 min left". */
fun minutesLeftText(ms: Long): String = "${(ms.coerceAtLeast(0L) + 59_999L) / 60_000L} min left"
