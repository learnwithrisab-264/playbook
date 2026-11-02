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
        assertTrue(allows("com.google.android.inputmethod.latin"))
    }

    @Test
    fun otherAppsHomeAndSettingsAreBlocked() {
        assertFalse(allows("com.whatsapp"))
        assertFalse(allows("com.android.launcher"))
        assertFalse(allows("com.android.settings"))
    }

    @Test
    fun exitNeedsBothThePhraseAndTheWait() {
        assertFalse(exitReady(EXIT_PHRASE, EXIT_WAIT_SECONDS - 1))
        assertFalse(exitReady("end it", EXIT_WAIT_SECONDS))
        assertTrue(exitReady(" end my study session ", EXIT_WAIT_SECONDS))
    }

    @Test
    fun timeLeftRoundsUp() {
        assertEquals("2 min left", minutesLeftText(120_000))
        assertEquals("1 min left", minutesLeftText(1_000))
        assertEquals("0 min left", minutesLeftText(0))
    }
}
