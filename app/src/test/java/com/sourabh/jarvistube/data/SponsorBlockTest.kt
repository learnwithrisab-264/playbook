package com.sourabh.jarvistube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorBlockTest {
    private val sponsor = SkipSegment(10.0, 40.0, "sponsor")
    private val filler = SkipSegment(60.0, 90.0, "filler")

    @Test
    fun skipsAChosenSegment() {
        assertEquals(sponsor, segmentToSkip(listOf(sponsor, filler), 15_000, DEFAULT_SKIP_CATEGORIES))
    }

    @Test
    fun leavesSegmentsYouDidNotChoose() {
        // Filler is off by default: it only shows on the seek bar.
        assertNull(segmentToSkip(listOf(sponsor, filler), 70_000, DEFAULT_SKIP_CATEGORIES))
        assertEquals(filler, segmentToSkip(listOf(sponsor, filler), 70_000, setOf("filler")))
    }

    @Test
    fun stopsJustBeforeTheEndSoItNeverLoops() {
        assertNull(segmentToSkip(listOf(sponsor), 39_600, DEFAULT_SKIP_CATEGORIES))
        assertNull(segmentToSkip(listOf(sponsor), 9_000, DEFAULT_SKIP_CATEGORIES))
    }

    @Test
    fun defaultsAreTheFiveSkippedBefore() {
        assertEquals(setOf("sponsor", "selfpromo", "interaction", "intro", "outro"), DEFAULT_SKIP_CATEGORIES)
        assertEquals("sponsor", sponsor.label)
        assertEquals("filler tangent", filler.label)
    }
}
