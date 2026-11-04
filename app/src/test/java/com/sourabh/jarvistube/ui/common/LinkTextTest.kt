package com.sourabh.jarvistube.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTextTest {
    @Test
    fun clockTimes() {
        assertEquals(754L, parseTimestamp("12:34"))
        assertEquals(3723L, parseTimestamp("1:02:03"))
        assertNull(parseTimestamp("12:75"))
    }

    @Test
    fun youtubeIds() {
        assertEquals("dQw4w9WgXcQ", youtubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", youtubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", youtubeVideoId("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(youtubeVideoId("https://www.youtube.com/@somechannel"))
        assertNull(youtubeVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun startTimes() {
        assertEquals(42L, linkStartSeconds("https://youtu.be/dQw4w9WgXcQ?t=42"))
        assertEquals(754L, linkStartSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12m34s"))
    }

    @Test
    fun linksAndTimesInText() {
        val found = findLinks("Intro 0:00, notes at https://example.com/a. Part two 1:02:03")
        assertEquals(listOf(0L, null, 3723L), found.map { it.seconds })
        assertEquals("https://example.com/a", found[1].url)
    }
}
