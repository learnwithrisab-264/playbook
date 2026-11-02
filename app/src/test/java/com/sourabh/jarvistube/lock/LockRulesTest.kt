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
    fun timeLeftRoundsUp() {
        assertEquals("2 min left", minutesLeftText(120_000))
        assertEquals("1 min left", minutesLeftText(1_000))
        assertEquals("0 min left", minutesLeftText(0))
    }
}
