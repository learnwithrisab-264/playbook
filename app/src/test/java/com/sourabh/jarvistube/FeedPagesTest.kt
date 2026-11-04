package com.sourabh.jarvistube

import com.sourabh.jarvistube.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedPagesTest {
    private fun v(n: Int) = VideoItem("u$n", "t$n", "c", null, 100, 0, null, false)

    @Test
    fun newPagesAreKeptAndOldOnesComeBackFromMemory() {
        var feed = FeedState(items = (1..3).map(::v))
        feed = feed.withNewPage(feed.items + (4..5).map(::v))
        assertEquals(2, feed.pageCount)
        assertEquals(1, feed.page)
        assertEquals(listOf("u4", "u5"), feed.pageItems().map { it.url })
        feed = feed.showPage(0)
        assertEquals(listOf("u1", "u2", "u3"), feed.pageItems().map { it.url })
    }

    @Test
    fun nothingNewAddsNoEmptyPage() {
        val feed = FeedState(items = (1..3).map(::v), loadingMore = true)
        val after = feed.withNewPage(feed.items)
        assertEquals(1, after.pageCount)
        assertEquals(false, after.loadingMore)
    }
}
