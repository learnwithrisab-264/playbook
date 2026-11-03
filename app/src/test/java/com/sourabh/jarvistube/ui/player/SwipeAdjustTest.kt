package com.sourabh.jarvistube.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeAdjustTest {
    @Test
    fun sideDecidesWhatChanges() {
        assertEquals(SwipeZone.Brightness, swipeZoneAt(100f, 1000f))
        assertEquals(SwipeZone.Exit, swipeZoneAt(500f, 1000f))
        assertEquals(SwipeZone.Volume, swipeZoneAt(900f, 1000f))
    }

    @Test
    fun swipingUpRaisesAndDownLowers() {
        assertEquals(0.75f, adjustLevel(0.5f, dy = -200f, height = 1000f), 0.001f)
        assertEquals(0.25f, adjustLevel(0.5f, dy = 200f, height = 1000f), 0.001f)
    }

    @Test
    fun levelStaysBetweenZeroAndFull() {
        assertEquals(1f, adjustLevel(0.9f, dy = -5000f, height = 1000f), 0.001f)
        assertEquals(0f, adjustLevel(0.1f, dy = 5000f, height = 1000f), 0.001f)
    }
}
