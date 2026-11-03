package com.sourabh.jarvistube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FeedStoreTest {
    private val accountA = "cd7d0053-4121-4210-9d59-2aa6f0e30d3e"
    private val accountB = "44997760-372f-4ead-b0ac-413df6d8a82e"

    @Test
    fun twoAccountsNeverShareAFeedFile() {
        for (feed in Feed.entries) {
            assertNotEquals(FeedStore.fileName(feed, accountA, "US"), FeedStore.fileName(feed, accountB, "US"))
        }
    }

    @Test
    fun theSameAccountFindsItsOwnFileAgain() {
        assertEquals(FeedStore.fileName(Feed.Recommended, accountA, "US"), FeedStore.fileName(Feed.Recommended, accountA, "US"))
    }

    @Test
    fun feedsAndRegionsAreSeparateToo() {
        assertNotEquals(FeedStore.fileName(Feed.Recommended, accountA, "US"), FeedStore.fileName(Feed.Shorts, accountA, "US"))
        assertNotEquals(FeedStore.fileName(Feed.Recommended, accountA, "US"), FeedStore.fileName(Feed.Recommended, accountA, "IN"))
    }

    @Test
    fun seenListsOfTwoAccountsStayApart() {
        val a = SeenList(10).apply { add("videoOfA") }
        val b = SeenList(10).apply { decode("") }
        assertEquals(false, "videoOfA" in b)
        assertEquals(true, "videoOfA" in a)
    }
}
