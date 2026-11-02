package com.sourabh.jarvistube.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {
    private val own = "com.sourabh.jarvistube"
    private val dialer = "com.google.android.dialer"

    private fun allows(pkg: String) = lockAllows(pkg, own, dialer, setOf("com.google.android.inputmethod.latin"))

    @Test
    fun playbookPhoneAndKeyboardStayAllowed() {
        assertTrue(allows(own))
        assertTrue(allows(dialer))
        assertTrue(allows("com.android.server.telecom"))
        assertTrue(allows("com.android.phone"))
        assertTrue(allows("com.google.android.inputmethod.latin"))
    }

    @Test
    fun otherAppsHomeAndSettingsAreBlocked() {
        assertFalse(allows("com.whatsapp"))
        assertFalse(allows("com.android.launcher"))
        assertFalse(allows("com.android.settings"))
    }

    @Test
    fun movingTheClockForwardCantShortenASession() {
        val hour = 3_600_000L
        // After a restart with no internet, a clock pushed forward says the session is over.
        assertEquals(hour, lockLeftMs(byClock = -hour, floor = hour, clockTrusted = false))
    }

    @Test
    fun aTrustedClockDecides() {
        val hour = 3_600_000L
        // Time passed while the phone was off still counts once the real time is known.
        assertEquals(0L, lockLeftMs(byClock = -hour, floor = hour, clockTrusted = true))
        assertEquals(hour, lockLeftMs(byClock = hour, floor = 0L, clockTrusted = true))
    }

    @Test
    fun exitNeedsBothThePhraseAndTheWait() {
        assertFalse(exitReady(EXIT_PHRASE, EXIT_WAIT_SECONDS - 1))
        assertFalse(exitReady("end it", EXIT_WAIT_SECONDS))
        assertTrue(exitReady(" end my study session ", EXIT_WAIT_SECONDS))
    }

    @Test
    fun exitIsFreeWhenNeverUsed() {
        assertEquals(0L, exitWaitMs(emptyList(), 1_000_000L))
    }

    @Test
    fun exitWorksOnceIn24Hours() {
        val hour = 3_600_000L
        val now = 100 * 24 * hour
        assertEquals(24 * hour, exitWaitMs(listOf(now), now))
        assertEquals(hour, exitWaitMs(listOf(now - 23 * hour), now))
        assertEquals(0L, exitWaitMs(listOf(now - 24 * hour), now))
    }

    @Test
    fun exitWorksThreeTimesIn7Days() {
        val day = 86_400_000L
        val now = 100 * day
        // Used 6, 4 and 2 days ago: the next one waits until the oldest is 7 days old.
        assertEquals(day, exitWaitMs(listOf(now - 6 * day, now - 4 * day, now - 2 * day), now))
        // Two in the week still leaves one.
        assertEquals(0L, exitWaitMs(listOf(now - 4 * day, now - 2 * day), now))
        // Uses older than a week no longer count.
        assertEquals(0L, exitWaitMs(listOf(now - 8 * day, now - 4 * day, now - 2 * day), now))
    }

    @Test
    fun timeLeftRoundsUp() {
        assertEquals("2 min left", minutesLeftText(120_000))
        assertEquals("1 min left", minutesLeftText(1_000))
        assertEquals("0 min left", minutesLeftText(0))
    }
}
